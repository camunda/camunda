import { defineConfig } from "@kubb/core";
import { pluginOas } from "@kubb/plugin-oas";
import { pluginTs } from "@kubb/plugin-ts";
import { pluginZod } from "@kubb/plugin-zod";
import { pluginFaker } from "@kubb/plugin-faker";
import { pluginMsw } from "@kubb/plugin-msw";

export default defineConfig(() => {
  return {
    root: ".",
    input: {
      path: "./rest-api.yaml",
    },
    output: {
      path: "./src/gen",
    },
    plugins: [
      pluginOas(),
      pluginTs(),
      pluginZod({
        output: {
          path: "./zod",
        },
      }),
      pluginFaker({
        output: {
          path: "./mocks",
          barrelType: "named",
          banner: "/* eslint-disable no-alert, no-console */",
          footer: "",
        },
        group: {
          type: "tag",
          name: ({ group }) => `${group}Service`,
        },
        dateType: "date",
        unknownType: "unknown",
        seed: [100],
      }),
      pluginMsw({
        output: {
          path: "./msw",
          barrelType: "named",
          banner: "/* eslint-disable no-alert, no-console */",
          footer: "",
        },
        group: {
          type: "tag",
          name: ({ group }) => `${group}Service`,
        },
        handlers: true,
      }),
    ],
  };
});
