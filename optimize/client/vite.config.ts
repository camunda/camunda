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
import sbom from 'rollup-plugin-sbom';

const outDir = 'dist';

const backend = 'http://localhost:8090';
const sessionCookies = ['X-CSRF-TOKEN', 'X-Optimize-Authorization_0', 'X-Optimize-Refresh-Token'];
// Per client, so a 401 in one browser (e.g. an expired session) does not affect the others.
const rejectedSessions = new Set<string>();

function getSessionKey(cookieHeader?: string): string | undefined {
  const cookies = (cookieHeader ?? '').split(';').map((cookie) => cookie.trim());
  const session = cookies.filter((cookie) =>
    sessionCookies.some((name) => cookie.startsWith(`${name}=`))
  );
  return session.length > 0 ? session.join(';') : undefined;
}

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
  plugins: mode === 'sbom' ? [...plugins, sbom({specVersion: '1.6'}) as PluginOption] : plugins,
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
        target: backend,
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes, req) => {
            const session = getSessionKey(req.headers.cookie);
            if (!session) {
              return;
            }
            if (proxyRes.statusCode === 401) {
              rejectedSessions.add(session);
            } else {
              rejectedSessions.delete(session);
            }
          });
        },
      },
      '^/': {
        target: backend,
        bypass: (req) => {
          const path = req.url;
          if (path?.includes('/sso-callback') || path?.includes('/logout')) {
            return;
          }

          const session = getSessionKey(req.headers.cookie);
          if (session && !rejectedSessions.has(session)) {
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
