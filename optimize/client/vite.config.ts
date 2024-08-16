/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {defineConfig, transformWithEsbuild} from 'vite';
import react from '@vitejs/plugin-react';
import svgr from 'vite-plugin-svgr';

export default defineConfig({
  base: '',
  plugins: [
    {
      name: 'treat-js-files-as-jsx',
      async transform(code, id) {
        if (!id.match(/src\/.*\.js$/)) return null;

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
  ],
  optimizeDeps: {
    force: true,
    esbuildOptions: {
      loader: {
        '.js': 'jsx',
      },
    },
  },
  resolve: {
    alias: {
      components: '/src/modules/components',
      filter: '/src/modules/filter',
      HOC: '/src/modules/HOC',
      hooks: '/src/modules/hooks',
      notifications: '/src/modules/notifications',
      onboarding: '/src/modules/onboarding',
      polyfills: '/src/modules/polyfills',
      prompt: '/src/modules/prompt',
      saveGuard: '/src/modules/saveGuard',
      services: '/src/modules/services',
      'shared-styles': '/src/modules/shared-styles',
      theme: '/src/modules/theme',
      tracking: '/src/modules/tracking',
      translation: '/src/modules/translation',
      config: '/src/modules/config.tsx',
      dates: '/src/modules/dates.ts',
      debouncePromise: '/src/modules/debouncePromise.ts',
      request: '/src/modules/request.ts',
      types: '/src/modules/types.ts',
      variables: '/src/modules/variables.ts',
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
            req.headers.cookie?.includes('X-Optimize-Authorization') ||
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
});
