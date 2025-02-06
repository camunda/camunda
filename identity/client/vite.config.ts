import { defineConfig, UserConfig } from "vite";

import react from "@vitejs/plugin-react";
import license from "rollup-plugin-license";
import path from "node:path";
import sbom from "@vzeta/rollup-plugin-sbom";

const outDir = "dist";
const contextPath = process.env.CONTEXT_PATH ?? "";
const proxyPath = `^${contextPath}/(v2|login|logout).*`;

// https://vitejs.dev/config/
export default defineConfig(
  ({ mode }): UserConfig => ({
    base: "",
    plugins: mode === "sbom" ? [react(), sbom()] : [react()],
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
        }),
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
      },
    },
  }),
);
