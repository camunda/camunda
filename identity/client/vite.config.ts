/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { defineConfig, PluginOption, UserConfig } from "vite";
import svgr from "vite-plugin-svgr";
import react from "@vitejs/plugin-react";
import license from "rollup-plugin-license";
import path from "node:path";
import sbom from "@vzeta/rollup-plugin-sbom";

const outDir = "dist";
const contextPath = process.env.CONTEXT_PATH ?? "";
const proxyPath = `^${contextPath}/(v2|login|logout).*`;
const configPath = `^${contextPath}/config.js`

const plugins: PluginOption[] = [
  react(),
  svgr({
    svgrOptions: {
      exportType: "default",
      ref: true,
      svgo: false,
      titleProp: true,
    },
    include: "**/*.svg",
  }),
];

// https://vitejs.dev/config/
export default defineConfig(
  ({ mode }): UserConfig => ({
    base: "",
    plugins: mode === "sbom" ? [...plugins, sbom() as PluginOption] : plugins,
    resolve: {
      alias: {
        src: "/src",
      },
    },
    build: {
      outDir,
      rollupOptions: {
        plugins: license({
          thirdParty: {
            output: path.resolve(
              __dirname,
              `./${outDir}/assets/vendor.LICENSE.txt`,
            ),
          },
        }) as PluginOption,
      },
    },
    esbuild: {
      banner: "/*! licenses: /assets/vendor.LICENSE.txt */",
      legalComments: "none",
    },
    server: {
      proxy: {
        [proxyPath]: {
          target: "http://localhost:8080",
          changeOrigin: true,
        },
        [configPath]: {
          target: "http://localhost:8080/identity",
          changeOrigin: true,
        }
      },
    },
  }),
);
