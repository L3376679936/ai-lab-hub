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
    console.log('✔ 连接服务器成功，准备读取 Java 异常日志...');
    conn.exec('tail -n 100 /opt/ai-lab-hub/output.log', (err, stream) => {
        if (err) {
            console.error('读取日志命令异常:', err.message);
            conn.end();
            return;
        }

        stream.on('data', (data) => {
            process.stdout.write(data.toString());
        }).on('close', () => {
            conn.end();
        }).stderr.on('data', (data) => {
            process.stderr.write(data.toString());
        });
    });
}).on('error', (err) => {
    console.error('连接服务器失败:', err.message);
}).connect(connSettings);
