try {
    const bcrypt = require('bcryptjs');
    const hash = bcrypt.hashSync('admin123', 10);
    console.log(hash);
} catch (e) {
    console.log('需要安装 bcryptjs: ' + e.message);
}
