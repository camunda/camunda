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
import tsconfigPaths from 'vite-tsconfig-paths';
import svgr from 'vite-plugin-svgr';
import browserslistToEsbuild from 'browserslist-to-esbuild';
import license from 'rollup-plugin-license';
import path from 'node:path';
import sbom from 'rollup-plugin-sbom';
import {configDefaults} from 'vitest/config';
import {playwright} from '@vitest/browser-playwright';

const plugins: PluginOption[] = [react(), tsconfigPaths(), svgr()];
const outDir = 'build';

function getReporters(): Pick<
  NonNullable<UserConfig['test']>,
  'reporters' | 'outputFile'
> {
  if (process.env.CI) {
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
    rollupOptions: {
      input: {
        index:
          mode === 'visual-regression' ? './index.html' : './index.prod.html',
      },
      plugins: [
        license({
          thirdParty: {
            output: path.resolve(
              __dirname,
              `./${outDir}/assets/vendor.LICENSE.txt`,
            ),
          },
        }),
      ],
    },
    target: browserslistToEsbuild(),
    sourcemap: mode !== 'sbom',
  },
  esbuild: {
    banner: '/*! licenses: /assets/vendor.LICENSE.txt */',
    legalComments: 'none',
  },
  resolve: {alias: {src: path.resolve(__dirname, './src')}},
  test: {
    globals: true,
    restoreMocks: true,
    mockReset: true,
    unstubGlobals: true,
    clearMocks: true,
    resetMocks: true,
    unstubEnvs: true,
    dangerouslyIgnoreUnhandledErrors: Boolean(process.env.CI),
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
