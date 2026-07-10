const { Client } = require('ssh2');
const fs = require('fs');
const path = require('path');

// 1. 定义连接配置
const connSettings = {
    host: '103.47.81.55',
    port: 29367,
    username: 'root',
    password: 'Gp%674268442533',
    readyTimeout: 30000
};

// 2. 本地文件与远程路径定义
const localJarPath = path.resolve(__dirname, '../ai-lab-hub-bootstrap/target/ai-lab-hub-bootstrap-1.0.0.jar');
const localMcpScript = path.resolve(__dirname, './office-mcp.js');
const localMcpPackage = path.resolve(__dirname, './package.json');
const localFrontendZip = path.resolve(__dirname, './frontend.zip');

const remoteBaseDir = '/opt/ai-lab-hub';
const remoteJarPath = '/opt/ai-lab-hub/ai-lab-hub-bootstrap-1.0.0.jar';
const remoteMcpScript = '/opt/ai-lab-hub/mcp/office-mcp.js';
const remoteMcpPackage = '/opt/ai-lab-hub/mcp/package.json';
const remoteFrontendZip = '/opt/ai-lab-hub/frontend.zip';

const conn = new Client();

conn.on('ready', () => {
    console.log('✔ SSH 连接服务器成功!');

    // 第一步：执行远程目录准备与 SFTP 上传
    conn.sftp((err, sftp) => {
        if (err) {
            console.error('SFTP 启动失败:', err);
            conn.end();
            process.exit(1);
        }

        console.log('准备创建远程服务器发布路径...');
        // 远程创建文件夹
        conn.exec(`mkdir -p ${remoteBaseDir}/mcp`, (err, stream) => {
            if (err) {
                console.error('创建远程目录失败:', err);
                conn.end();
                process.exit(1);
            }

            stream.on('close', async () => {
                console.log('✔ 远程目录创建成功.');
                try {
                    // 开始依次上传文件
                    await uploadFile(sftp, localJarPath, remoteJarPath, 'Java Fat Jar 引导包');
                    await uploadFile(sftp, localMcpScript, remoteMcpScript, 'Node MCP 业务脚本');
                    await uploadFile(sftp, localMcpPackage, remoteMcpPackage, 'Node MCP package.json');
                    await uploadFile(sftp, localFrontendZip, remoteFrontendZip, '前端静态资源压缩包');
                    
                    console.log('✔ 所有发布文件上传成功. 准备拉起远程部署逻辑...');
                    runRemoteDeployCommands();

                } catch (uploadErr) {
                    console.error('上传文件异常退出:', uploadErr.message);
                    conn.end();
                    process.exit(1);
                }
            }).resume();
        });
    });
}).on('error', (err) => {
    console.error('✘ 连接服务器失败:', err.message);
    process.exit(1);
}).connect(connSettings);

/**
 * 封装 SFTP 文件传输进度打印
 */
function uploadFile(sftp, localPath, remotePath, fileLabel) {
    return new Promise((resolve, reject) => {
        if (!fs.existsSync(localPath)) {
            return reject(new Error(`本地文件不存在: ${localPath}`));
        }

        console.log(`开始上传 [${fileLabel}] -> ${remotePath}...`);
        const stats = fs.statSync(localPath);
        const fileSize = stats.size;

        sftp.fastPut(localPath, remotePath, {
            chunkSize: 32768,
            concurrency: 64,
            step: (transferred, chunk, total) => {
                const percent = ((transferred / fileSize) * 100).toFixed(1);
                process.stdout.write(`  上传进度: ${percent}% (${(transferred / 1024 / 1024).toFixed(2)} MB / ${(fileSize / 1024 / 1024).toFixed(2)} MB)\r`);
            }
        }, (err) => {
            if (err) {
                console.log(); // 换行
                return reject(err);
            }
            console.log(`\n✔ [${fileLabel}] 上传成功!`);
            resolve();
        });
    });
}

/**
 * 执行远程依赖安装、旧进程查杀、以及 Java 服务挂载拉起
 */
function runRemoteDeployCommands() {
    // 组装远程 Shell 执行逻辑
    const cmd = `
        echo "=== 0. 检查并安装 Java (JDK 8) 环境 ==="
        if ! command -v java &> /dev/null; then
            echo "未检测到 Java 环境，开始拉起系统包管理器进行安装..."
            if command -v yum &> /dev/null; then
                yum install -y java-1.8.0-openjdk
            elif command -v apt-get &> /dev/null; then
                apt-get update && apt-get install -y openjdk-8-jre-headless
            else
                echo "✘ 错误：无法自动安装 Java，请登录服务器手动安装"
                exit 1
            fi
        fi
        echo "✔ Java 环境检查通过: $(java -version 2>&1 | head -n 1)"

        echo "=== 1. 检查并安装 Node MCP 依赖 ==="
        cd ${remoteBaseDir}/mcp
        if command -v pnpm &> /dev/null; then
            pnpm install --prod
        else
            npm install --production
        fi

        echo "=== 1.5. 部署并解压前端静态资源 ==="
        mkdir -p /data/frontend
        unzip -o ${remoteFrontendZip} -d /data/frontend
        rm -f ${remoteFrontendZip}

        echo "=== 2. 查杀同端口/同名称的旧 Java 进程 ==="
        PID=$(ps -ef | grep ai-lab-hub-bootstrap | grep -v grep | awk '{print $2}')
        if [ -n "$PID" ]; then
            echo "发现旧进程 $PID，正在强行杀掉..."
            kill -9 $PID
        fi

        # 防御性杀掉 3000 端口占用 (Nginx 代理此端口)
        PORT_PID=$(lsof -t -i:3000 2>/dev/null)
        if [ -n "$PORT_PID" ]; then
            echo "发现 3000 端口占用进程 $PORT_PID，正在强制释放..."
            kill -9 $PORT_PID
        fi

        echo "=== 3. 挂载后台拉起新的 Java 进程 ==="
        cd ${remoteBaseDir}
        nohup java -jar ai-lab-hub-bootstrap-1.0.0.jar --server.port=3000 > output.log 2>&1 &

        echo "进程拉起成功，等待 5 秒校验状态..."
        sleep 5

        echo "=== 4. 远程日志打印 ==="
        head -n 25 output.log

        echo "=== 5. 校验 3000 端口绑定与运行进程 ==="
        ps -ef | grep ai-lab-hub-bootstrap | grep -v grep
    `;

    console.log('向远程服务器下发部署Shell脚本...');
    conn.exec(cmd, (err, stream) => {
        if (err) {
            console.error('下发部署命令异常:', err.message);
            conn.end();
            process.exit(1);
        }

        stream.on('data', (data) => {
            process.stdout.write(data.toString());
        }).on('close', () => {
            console.log('=== 远程部署流程下发完毕 ===');
            conn.end();
            process.exit(0);
        }).stderr.on('data', (data) => {
            process.stderr.write(data.toString());
        });
    });
}
