import type { StorybookConfig } from "@storybook/react-webpack5"

const config: StorybookConfig = {
	stories: ["../src/**/*.mdx", "../src/**/*.stories.@(js|jsx|mjs|ts|tsx)"],
	addons: [
		"@storybook/addon-webpack5-compiler-swc",
		"@storybook/addon-links",
		"@chromatic-com/storybook",
		{
			name: "@storybook/addon-styling-webpack",
			options: {
				rules: [
					{
						test: /\.scss$/,
						use: ["style-loader", "css-loader", "sass-loader"],
					},
				],
			},
		},
		"@storybook/addon-docs",
	],

	framework: {
		name: "@storybook/react-webpack5",
		options: {},
	},
	swc: () => ({
		jsc: {
			transform: {
				react: {
					runtime: "automatic",
				},
			},
		},
	}),
	webpackFinal: async (config) => {
		if (config.resolve) {
			config.resolve.alias = {
				...(config.resolve.alias ?? {}),
				src: new URL("../src", import.meta.url).pathname,
			}
		}
		return config
	},
}
export default config
