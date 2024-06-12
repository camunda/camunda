/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/// <reference types="vitest" />
/// <reference types="vite/client" />

import {defineConfig} from 'vite';
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
      '/client-config.js': 'http://localhost:8080/tasklist',
    },
  },
  build: {
    outDir: 'build',
    rollupOptions: {
      input: {
        index: './index.prod.html',
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
        'src/modules/mockServer/startBrowserMocking.tsx',
      ],
      reporters: ['html', 'default', 'hanging-process'],
      all: true,
      lines: 80,
      functions: 80,
      branches: 80,
      statements: 80,
    },
  },
}));
