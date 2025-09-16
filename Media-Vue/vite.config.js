import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  server: {
    port: 80,
    proxy: { //代理
      '/api': {
        target: 'http://10.10.10.103', //目标地址
        secure: false, //https
        changeOrigin: true, //跨域
        //rewrite: path => path.replace(/^\/api/, '') //重写路径
      }
    }
  }
})
