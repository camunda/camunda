/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type PluginOption, defineConfig, transformWithEsbuild} from 'vite';
import react from '@vitejs/plugin-react';
import svgr from 'vite-plugin-svgr';
import {readdirSync} from 'node:fs';
import license from 'rollup-plugin-license';
import path from 'node:path';
import sbom from '@vzeta/rollup-plugin-sbom';

const outDir = 'dist';

const plugins: PluginOption[] = [
  {
    name: 'treat-js-files-as-jsx',
    async transform(code, id) {
      if (!id.match(/src\/.*\.js$/)) {
        return null;
      }

      // Use the exposed transform from vite, instead of directly
      // transforming with esbuild
      return transformWithEsbuild(code, id, {
        loader: 'jsx',
        jsx: 'automatic',
      });
    },
  },
  react(),
  svgr({
    svgrOptions: {exportType: 'default', ref: true, svgo: false, titleProp: true},
    include: '**/*.svg',
  }),
];

export default defineConfig(({mode}) => ({
  base: '',
  build: {
    outDir,
    // The backend only allow public resources inside the static folder
    assetsDir: 'static',
    rollupOptions: {
      plugins: license({
        thirdParty: {
          output: path.resolve(__dirname, `./${outDir}/static/vendor.LICENSE.txt`),
        },
      }) as PluginOption,
    },
  },
  esbuild: {
    banner: '/*! licenses: /static/vendor.LICENSE.txt */',
    legalComments: 'none',
  },
  plugins: mode === 'sbom' ? [...plugins, sbom() as PluginOption] : plugins,
  optimizeDeps: {
    force: true,
    esbuildOptions: {
      loader: {
        '.js': 'jsx',
      },
    },
  },
  resolve: {
    alias: generateAliases(),
  },
  server: {
    port: 3000,
    open: true,
    proxy: {
      '^/(api|external/api|external/static)': {
        target: 'http://localhost:8090',
      },
      '^/': {
        target: 'http://localhost:8090',
        bypass: (req) => {
          const path = req.url;
          if (path?.includes('/sso-callback')) {
            return;
          }

          if (
            req.headers.cookie?.includes('X-Optimize-Authorization_0') ||
            req.headers.cookie?.includes('X-Optimize-Refresh-Token')
          ) {
            return path;
          }

          if (path === '/' || path?.includes('/sso/auth0')) {
            return;
          }
        },
      },
    },
  },
}));

// Function to generate aliases dynamically
function generateAliases() {
  const aliases: Record<string, string> = {};

  readdirSync('src/modules').forEach((item) => {
    const aliasKey = item.replace(/\.[^/.]+$/, ''); // Remove file extension if present
    aliases[aliasKey] = `/src/modules/${aliasKey}`;
  });

  return aliases;
}
