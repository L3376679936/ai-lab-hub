import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // 后端 context-path 是 /api，所以不需要去掉前缀
        rewrite: (path) => path.replace(/^\/api/, '/api')
      }
    }
  }
})
