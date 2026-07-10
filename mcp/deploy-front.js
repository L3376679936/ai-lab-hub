const { Client } = require('ssh2');
const fs = require('fs');
const path = require('path');

// 连接配置
const connSettings = {
    host: '103.47.81.55',
    port: 29367,
    username: 'root',
    password: 'Gp%674268442533',
    readyTimeout: 30000
};

const localFrontendZip = path.resolve(__dirname, './frontend.zip');
const remoteFrontendZip = '/opt/ai-lab-hub/frontend.zip';

const conn = new Client();

conn.on('ready', () => {
    console.log('✔ SSH 连接成功，准备上传前端静态资源...');

    conn.sftp((err, sftp) => {
        if (err) { console.error('SFTP 启动失败:', err); conn.end(); return; }

        const stats = fs.statSync(localFrontendZip);
        const fileSize = stats.size;
        console.log(`开始上传 frontend.zip (${(fileSize / 1024).toFixed(1)} KB)...`);

        sftp.fastPut(localFrontendZip, remoteFrontendZip, {
            chunkSize: 32768,
            concurrency: 64,
            step: (transferred) => {
                const pct = ((transferred / fileSize) * 100).toFixed(1);
                process.stdout.write(`  进度: ${pct}%\r`);
            }
        }, (err) => {
            if (err) { console.error('\n上传失败:', err); conn.end(); return; }
            console.log('\n✔ frontend.zip 上传成功，开始远程解压...');

            const cmd = `
                rm -rf /data/frontend/ai-lab-hub
                unzip -o ${remoteFrontendZip} -d /data/frontend
                rm -f ${remoteFrontendZip}
                echo "✔ 前端静态资源解压完毕"
                ls /data/frontend/ai-lab-hub/
            `;

            conn.exec(cmd, (err, stream) => {
                if (err) { console.error('远程命令失败:', err); conn.end(); return; }
                stream.on('data', d => process.stdout.write(d.toString()))
                      .stderr.on('data', d => process.stderr.write(d.toString()));
                stream.on('close', () => {
                    console.log('=== 前端热更新部署完毕 ===');
                    conn.end();
                });
            });
        });
    });
}).on('error', err => {
    console.error('连接失败:', err.message);
}).connect(connSettings);
