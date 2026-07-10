const { Client } = require('ssh2');

const connSettings = {
    host: '103.47.81.55',
    port: 29367,
    username: 'root',
    password: 'Gp%674268442533',
    readyTimeout: 30000
};

// 要注入到 Nginx 配置文件的 location 块内容
const nginxLocationBlocks = `
    # ============ AI-Lab-Hub 项目 ============
    # 前端静态资源（专属目录 /ai-lab-hub）
    location /ai-lab-hub {
        alias /data/frontend/ai-lab-hub/;
        index index.html;
        try_files $uri $uri/ /ai-lab-hub/index.html;
    }

    location ~* ^/ai-lab-hub/.*\\.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
        alias /data/frontend/;
        expires 7d;
        add_header Cache-Control "public, max-age=604800";
    }

    # 后端 API 反向代理（专属端口 8081，避免与 New API 的 3000 端口冲突）
    location /ai-lab-hub-api/ {
        proxy_pass http://127.0.0.1:8081/ai-lab-hub-api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        # SSE 流式输出关键配置 - 禁止 Nginx 缓冲区积压
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_http_version 1.1;
        chunked_transfer_encoding on;
    }
`;

// Python3 脚本：幂等注入 location 块到 server {} 的 access_log 行前面
const pythonScript = `
import re

conf_path = '/etc/nginx/conf.d/lab-project.conf'
marker = '# ============ AI-Lab-Hub 项目 ============'
insert_before = '    access_log'
new_block = """${nginxLocationBlocks.replace(/\\/g, '\\\\').replace(/`/g, '\\`').replace(/\$/g, '\\$').replace(/"/g, '\\"')}"""

with open(conf_path, 'r') as f:
    content = f.read()

if marker in content:
    print('ai-lab-hub Nginx 块已存在，跳过注入')
else:
    # 在 access_log 行之前插入
    idx = content.find(insert_before)
    if idx == -1:
        print('ERROR: 未找到 access_log 插入位置')
        exit(1)
    new_content = content[:idx] + new_block + '\\n' + content[idx:]
    with open(conf_path, 'w') as f:
        f.write(new_content)
    print('ai-lab-hub Nginx 专属路由块注入成功')
`;

const conn = new Client();

conn.on('ready', () => {
    console.log('✔ 连接服务器成功，准备用 Python3 注入 Nginx 配置...');
    
    // 1. 写 Python 脚本到远程临时文件
    const writeCmd = `cat > /tmp/patch_nginx.py << 'PYEOF'
import re

conf_path = '/etc/nginx/conf.d/lab-project.conf'
marker = '# AI-Lab-Hub'
insert_before = '    access_log'
new_block = """
    # ============ AI-Lab-Hub 项目 ============
    # 前端静态资源（专属目录 /ai-lab-hub）
    location /ai-lab-hub {
        alias /data/frontend/ai-lab-hub/;
        index index.html;
        try_files $uri $uri/ /ai-lab-hub/index.html;
    }

    location ~* ^/ai-lab-hub/.*\\\\.(js|css|png|jpg|jpeg|gif|ico|svg)$ {
        root /data/frontend;
        rewrite ^/ai-lab-hub/(.*) /ai-lab-hub/$1 break;
        expires 7d;
        add_header Cache-Control "public, max-age=604800";
    }

    # 后端 API 反向代理（专属端口 8081，避免与 New API 3000 冲突）
    location /ai-lab-hub-api/ {
        proxy_pass http://127.0.0.1:8081/ai-lab-hub-api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_http_version 1.1;
        chunked_transfer_encoding on;
    }

"""

with open(conf_path, 'r') as f:
    content = f.read()

if marker in content:
    print('ai-lab-hub Nginx 块已存在，跳过注入')
else:
    idx = content.find(insert_before)
    if idx == -1:
        print('ERROR: 未找到 access_log 插入位置')
        exit(1)
    new_content = content[:idx] + new_block + content[idx:]
    with open(conf_path, 'w') as f:
        f.write(new_content)
    print('ai-lab-hub Nginx 专属路由块注入成功')
PYEOF
python3 /tmp/patch_nginx.py
nginx -t && nginx -s reload
echo "=== Nginx 配置注入并热重载完成 ==="`; 

    conn.exec(writeCmd, (err, stream) => {
        if (err) {
            console.error('执行命令异常:', err.message);
            conn.end();
            return;
        }

        stream.on('data', (data) => {
            process.stdout.write(data.toString());
        }).stderr.on('data', (data) => {
            process.stderr.write(data.toString());
        }).on('close', (code) => {
            console.log(`\n=== 远程脚本执行完毕 (退出码 ${code}) ===`);
            conn.end();
        });
    });
}).on('error', (err) => {
    console.error('连接失败:', err.message);
}).connect(connSettings);
