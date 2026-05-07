/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/// <reference types="vitest" />
/// <reference types="vite/client" />

import {defineConfig, type PluginOption, type UserConfig} from 'vite';
import react from '@vitejs/plugin-react';
import svgr from 'vite-plugin-svgr';
import tailwindcss from '@tailwindcss/vite';
import sbom from 'rollup-plugin-sbom';
import {configDefaults} from 'vitest/config';
import {playwright} from '@vitest/browser-playwright';

const plugins: PluginOption[] = [react(), svgr(), tailwindcss()];
const outDir = 'build';

function getReporters(): Pick<
  NonNullable<UserConfig['test']>,
  'reporters' | 'outputFile'
> {
  if (process.env['CI']) {
    return {
      reporters: ['default', 'junit', 'github-actions'],
      outputFile: {
        junit: 'TEST-unit.xml',
      },
    };
  }
  return {
    reporters: ['default'],
  };
}

export default defineConfig(({mode}) => ({
  base: mode === 'production' ? './' : undefined,
  plugins: mode === 'sbom' ? [...plugins, sbom()] : plugins,
  preview: {
    port: 3003,
    open: false,
    proxy: {},
  },
  server: {
    port: 3000,
    open: true,
    proxy: {
      '/api': 'http://localhost:8080',
      '/v1': 'http://localhost:8080',
      '/v2': 'http://localhost:8080',
      '/login': {
        target: 'http://localhost:8080',
        bypass: (req) => (req.method !== 'POST' ? '/' : undefined),
      },
      '/logout': {
        target: 'http://localhost:8080',
        bypass: (req) => (req.method !== 'POST' ? '/' : undefined),
      },
      '/client-config.js': 'http://localhost:8080/operate',
    },
  },
  build: {
    outDir,
    sourcemap: mode !== 'sbom',
    license: {
      fileName: 'assets/vendor.LICENSE.txt',
    },
    rolldownOptions: {
      output: {
        postBanner: '/*! licenses: /assets/vendor.LICENSE.txt */',
      },
      input: {
        index:
          mode === 'visual-regression' ? './index.html' : './index.prod.html',
      },
    },
  },
  resolve: {
    tsconfigPaths: true,
    // `@camunda/design-system` is symlinked into node_modules and lives in a
    // sibling workspace (`webapp/client/`) that ships its own React 19 copy.
    // Without dedupe, Vite would resolve `react`/`react-dom` from each side
    // separately, producing two React instances and "Invalid hook call" errors.
    dedupe: ['react', 'react-dom'],
  },
  test: {
    globals: true,
    restoreMocks: true,
    mockReset: true,
    unstubGlobals: true,
    clearMocks: true,
    resetMocks: true,
    unstubEnvs: true,
    retry: process.env['CI'] ? 3 : 0,
    ...getReporters(),
    server: {
      deps: {
        // this was necessary due to some issues with styled-components which appeared when bumping C3 on this https://github.com/camunda/camunda/pull/44663
        inline: ['@camunda/camunda-composite-components'],
      },
    },
    projects: [
      {
        extends: true,
        test: {
          name: 'unit',
          environment: 'jsdom',
          include: ['./src/**/*.{test,spec}.?(c|m)[jt]s?(x)'],
          exclude: [
            ...configDefaults.exclude,
            './src/**/*.browser.{test,spec}.?(c|m)[jt]s?(x)',
          ],
          setupFiles: ['./src/setupTests.tsx'],
        },
      },
      {
        extends: true,
        test: {
          name: 'browser',
          include: ['./src/**/*.browser.{test,spec}.?(c|m)[jt]s?(x)'],
          setupFiles: ['./vitest.browser.setup.ts'],
          browser: {
            enabled: true,
            provider: playwright(),
            instances: [
              {
                browser: 'chromium',
              },
            ],
          },
        },
      },
    ],
  },
}));
