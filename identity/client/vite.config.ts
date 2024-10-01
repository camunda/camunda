import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import license from "rollup-plugin-license";
import path from "node:path";
import sbom from "@vzeta/rollup-plugin-sbom";

const outDir = "dist";

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => ({
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
      "/v2": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
}));
