import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiProxyTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:8080'
  const fileProxyTarget = env.VITE_FILE_PROXY_TARGET || apiProxyTarget
  const devPort = Number(env.VITE_DEV_PORT || 80)

  return {
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
      port: devPort,
      proxy: {
        '/api': {
          target: apiProxyTarget,
          secure: false,
          changeOrigin: true,
        },
        '/content': {
          target: fileProxyTarget,
          secure: false,
          changeOrigin: true,
        },
      }
    }
  }
})
