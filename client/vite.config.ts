/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/// <reference types="vitest" />
/// <reference types="vite/client" />

import {defineConfig} from 'vitest/config';
import react from '@vitejs/plugin-react';
import tsconfigPaths from 'vite-tsconfig-paths';
import svgr from 'vite-plugin-svgr';
import browserslistToEsbuild from 'browserslist-to-esbuild';

export default defineConfig(({mode}) => ({
  base: mode === 'production' ? './' : undefined,
  plugins: [react(), tsconfigPaths(), svgr()],
  server: {
    port: 3000,
    open: true,
    proxy: {
      '/api': 'http://localhost:8080',
      '/v1': 'http://localhost:8080',
      '/client-config.js': 'http://localhost:8080',
    },
  },
  build: {
    outDir: 'build',
    rollupOptions: {
      input: {
        index: './index.prod.html',
      },
      output: {
        manualChunks(id) {
          if (
            id.includes('node_modules/monaco-editor') ||
            id.includes('node_modules/@monaco-editor')
          ) {
            return 'monaco-editor';
          }

          return undefined;
        },
      },
    },
    target: browserslistToEsbuild(),
    sourcemap: true,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    include: ['./src/**/*.{test,spec}.?(c|m)[jt]s?(x)'],
    setupFiles: ['./src/setupTests.ts'],
    restoreMocks: true,
    coverage: {
      provider: 'istanbul',
      exclude: [
        'playwright.config.ts',
        'renameProdIndex.mjs',
        'public/**',
        'e2e/**',
        'build/**',
      ],
      reporters: ['html', 'default'],
      all: true,
      lines: 80,
      functions: 80,
      branches: 80,
      statements: 80,
    },
  },
}));
