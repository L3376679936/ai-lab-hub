import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  base: '/ai-lab-hub/',
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/ai-lab-hub-api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/ai-lab-hub-api/, '/ai-lab-hub-api')
      }
    }
  }
})
