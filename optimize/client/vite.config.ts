/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type PluginOption, defineConfig, transformWithOxc} from 'vite';
import react from '@vitejs/plugin-react';
import svgr from 'vite-plugin-svgr';
import {readdirSync} from 'node:fs';
import sbom from '@vzeta/rollup-plugin-sbom';

const outDir = 'dist';

const plugins: PluginOption[] = [
  {
    name: 'treat-js-files-as-jsx',
    enforce: 'pre',
    async transform(code, id) {
      if (!id.match(/src\/.*\.js$/)) {
        return null;
      }
      return transformWithOxc(code, id, {lang: 'jsx'});
    },
  },
  react({include: /\.(js|jsx|ts|tsx)$/}),
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
    // Use esbuild for CSS minification as the older @carbon/react version has
    // CSS that Lightning CSS (the default in Vite 8) cannot parse
    // will be fixed with github.com/camunda/camunda/issues/54826
    cssMinify: 'esbuild',
    license: {
      fileName: 'static/vendor.LICENSE.txt',
    },
    rolldownOptions: {
      moduleTypes: {
        '.js': 'jsx',
      },
      output: {
        postBanner: '/*! licenses: /static/vendor.LICENSE.txt */',
      },
    },
  },
  plugins: mode === 'sbom' ? [...plugins, sbom() as PluginOption] : plugins,
  optimizeDeps: {
    force: true,
    rolldownOptions: {
      moduleTypes: {
        '.js': 'jsx',
      },
    },
  },
  resolve: {
    alias: {
      // Redirect @carbon/react/lib (CJS) to @carbon/react/es (ESM) so Vite 8's
      // Rolldown pre-bundler handles them correctly
      // will be fixed with github.com/camunda/camunda/issues/54826
      '@carbon/react/lib': '@carbon/react/es',
      ...generateAliases(),
    },
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
