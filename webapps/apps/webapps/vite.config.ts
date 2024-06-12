import {vitePlugin as remix} from '@remix-run/dev';
import {defineConfig} from 'vite';
import tsconfigPaths from 'vite-tsconfig-paths';
import {cjsInterop} from 'vite-plugin-cjs-interop';

export default defineConfig({
  server: {
    port: 3000,
    open: true,
    proxy: {
      '/api': 'http://localhost:8080',
      '/v1': 'http://localhost:8080',
      '/client-config.js': 'http://localhost:8080/tasklist',
    },
  },
  plugins: [
    remix({
      future: {
        v3_fetcherPersist: true,
        v3_relativeSplatPath: true,
        v3_throwAbortReason: true,
      },
      ssr: false,
    }),
    tsconfigPaths(),
  ],
  preview: {
    port: 4000,
  },
});
