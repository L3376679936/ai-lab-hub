const { Client } = require('ssh2');

const connSettings = {
    host: '103.47.81.55',
    port: 29367,
    username: 'root',
    password: 'Gp%674268442533',
    readyTimeout: 30000
};

const conn = new Client();

conn.on('ready', () => {
    console.log('✔ SSH 连接成功，开始重启 Java 服务...');

    const cmd = `
        echo "=== 1. 查杀旧 Java 进程 ==="
        PID=$(ps -ef | grep ai-lab-hub-bootstrap | grep -v grep | awk '{print $2}')
        if [ -n "$PID" ]; then
            echo "发现旧进程 $PID，正在杀掉..."
            kill -9 $PID
            sleep 2
        else
            echo "未发现旧进程"
        fi

        echo "=== 2. 重新启动 Java 服务 (端口 8081) ==="
        cd /opt/ai-lab-hub
        nohup java -jar ai-lab-hub-bootstrap-1.0.0.jar --server.port=8081 > output.log 2>&1 &
        echo "进程拉起，等待 10 秒让 Spring Boot 完成建表初始化..."
        sleep 10

        echo "=== 3. 查看启动日志 ==="
        cat output.log

        echo "=== 4. 校验进程 ==="
        ps -ef | grep ai-lab-hub-bootstrap | grep -v grep
    `;

    conn.exec(cmd, (err, stream) => {
        if (err) { console.error('命令执行失败:', err.message); conn.end(); return; }
        stream.on('data', d => process.stdout.write(d.toString()))
              .stderr.on('data', d => process.stderr.write(d.toString()));
        stream.on('close', () => {
            console.log('\n=== Java 服务重启完毕 ===');
            conn.end();
        });
    });
}).on('error', err => {
    console.error('连接失败:', err.message);
}).connect(connSettings);
