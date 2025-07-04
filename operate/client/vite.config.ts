/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/// <reference types="vite/client" />

import {defineConfig, type PluginOption} from 'vite';
import react from '@vitejs/plugin-react';
import tsconfigPaths from 'vite-tsconfig-paths';
import svgr from 'vite-plugin-svgr';
import browserslistToEsbuild from 'browserslist-to-esbuild';
import license from 'rollup-plugin-license';
import path from 'node:path';
import sbom from '@vzeta/rollup-plugin-sbom';

const plugins: PluginOption[] = [react(), tsconfigPaths(), svgr()];
const outDir = 'build';

export default defineConfig(({mode}) => ({
  base: mode === 'production' ? './' : undefined,
  plugins: mode === 'sbom' ? [...plugins, sbom() as PluginOption] : plugins,
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
        }) as PluginOption,
      ],
    },
    target: browserslistToEsbuild(),
    sourcemap: true,
  },
  esbuild: {
    banner: '/*! licenses: /assets/vendor.LICENSE.txt */',
    legalComments: 'none',
  },
  resolve: {alias: {src: path.resolve(__dirname, './src')}},
}));
