import type { StorybookConfig } from "@storybook/react-webpack5"

const config: StorybookConfig = {
	stories: ["../src/**/*.mdx", "../src/**/*.stories.@(js|jsx|mjs|ts|tsx)"],
	addons: [
        "@storybook/addon-webpack5-compiler-swc",
        "@storybook/addon-links",
        "@storybook/addon-essentials",
        "@chromatic-com/storybook",
        "@storybook/addon-interactions",
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
		}
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
	docs: {
		autodocs: "tag",
	},
}
export default config
