import type { Preview } from "@storybook/react-webpack5"
import "../src/index.scss"

const preview: Preview = {
	parameters: {
		controls: {
			matchers: {
				color: /(background|color)$/i,
				date: /Date$/,
			},
		},
	},
}

export default preview
