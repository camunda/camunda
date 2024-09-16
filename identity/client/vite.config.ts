import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import license from "rollup-plugin-license";
import path from "node:path";

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => ({
  base: "",
  plugins: [react()],
  resolve: {
    alias: {
      src: "/src",
    },
  },
  build: {
    rollupOptions: {
      plugins: license({
        thirdParty: {
          output:
            mode === "sbom"
              ? {
                  file: path.join(__dirname, "dist", "dependencies.csv"),
                  encoding: "utf-8",
                  template(dependencies) {
                    return dependencies
                      .map(
                        (dependency) =>
                          `"${dependency.name}","${dependency.version}","${dependency.license}"`,
                      )
                      .join("\n");
                  },
                }
              : path.resolve(__dirname, "./dist/assets/vendor.LICENSE.txt"),
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
