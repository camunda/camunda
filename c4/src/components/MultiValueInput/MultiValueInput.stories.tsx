import type { Meta, StoryObj } from "@storybook/react"
import { within } from "@storybook/testing-library"
import { expect, jest } from "@storybook/jest"

import MultiValueInput from "./MultiValueInput"

const meta: Meta<typeof MultiValueInput> = {
	component: MultiValueInput,
}

export default meta

type Story = StoryObj<typeof MultiValueInput>

export const mainStory: Story = {
	args: {
		onRemove: jest.fn(),
		values: [
			{ value: "first", label: "Tag label" },
			{ value: "second", label: "Tag label" },
			{ value: "third", label: "Tag label" },
		],
		tagButtonTitle: "testButton",
		titleText: "Multi value input title",
	},
	play: async ({ args, canvasElement }) => {
		const canvas = within(canvasElement)

		const triggerBtn = canvas.getAllByTitle("testButton").at(2)
		triggerBtn?.click()

		expect(args.onRemove).toHaveBeenCalledWith("third", 2)
	},
}

export const invalidInput: Story = {
	args: {
		onRemove: jest.fn(),
		values: [
			{ value: "first", label: "Tag label" },
			{ value: "second", label: "Tag label" },
			{ value: "third", label: "Error Tag label", invalid: true },
			{ value: "fourth", label: "Tag label" },
		],
		tagButtonTitle: "testButton",
		invalid: true,
		invalidText: "Third value is invalid",
	},
}
