import { Decorator } from "@storybook/react"

interface Options {
	width?: string
}

export default function StoryWrapper({
	width = "300px",
}: Options = {}): Decorator {
	return (Story) => (
		<div
			style={{
				width,
				padding: "1rem",
				border: "1px solid #ccc",
				borderRadius: "8px",
				background: "#fff",
			}}
		>
			<Story />
		</div>
	)
}
