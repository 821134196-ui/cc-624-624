import { defineConfig } from 'vite';
import { svelte } from '@sveltejs/vite-plugin-svelte';

// 开发:5173 端口,/api 代理到 Quarkus 8080
// 构建:直接输出到 Quarkus 静态资源目录,打包进同一个 jar,实现一条命令启动
export default defineConfig({
  plugins: [svelte()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  },
  build: {
    outDir: '../src/main/resources/META-INF/resources',
    emptyOutDir: true
  }
});
