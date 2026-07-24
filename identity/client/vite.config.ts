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
import sbom from "rollup-plugin-sbom";
import { Spec } from "@cyclonedx/cyclonedx-library";

// Pin the emitted CycloneDX specVersion to the newest version Snyk's `sbom test`
// decoder actually supports (1.4/1.5/1.6 - see docs.snyk.io/developer-tools/snyk-cli/commands/sbom-test).
// rollup-plugin-sbom v4 switched its own default to 1.7, which Snyk cannot decode
// yet ("failed to decode CycloneDX input: invalid specification version", INC-6679/INC-6677).
const sbomSpecVersion = Spec.Version.v1dot6;

const outDir = "dist";
const contextPath = process.env.CONTEXT_PATH ?? "";
const proxyPath = `^${contextPath}/(v2|login|logout).*`;
const configPath = `^${contextPath}/config.js`;

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
    plugins: mode === "sbom" ? [...plugins, sbom({ specVersion: sbomSpecVersion })] : plugins,
    resolve: {
      tsconfigPaths: true,
    },
    build: {
      outDir,
      license: {
        fileName: "assets/vendor.LICENSE.txt",
      },
      rolldownOptions: {
        output: {
          postBanner: "/*! licenses: /assets/vendor.LICENSE.txt */",
        },
      },
    },
    server: {
      proxy: {
        [proxyPath]: {
          target: "http://localhost:8080",
          changeOrigin: true,
        },
        [configPath]: {
          target: "http://localhost:8080/admin",
          changeOrigin: true,
        },
      },
    },
  }),
);
