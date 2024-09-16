import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import sbom from "rollup-plugin-sbom";

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => ({
  base: "",
  plugins: mode === "sbom" ? [react(), sbom()] : [react()],
  resolve: {
    alias: {
      src: "/src",
    },
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
