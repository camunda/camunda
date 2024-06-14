import type { Meta, StoryObj } from "@storybook/react"
import { within, expect } from "@storybook/test"

import * as test from "@storybook/test"

import MultiValueInput from "./MultiValueInput"

const meta: Meta<typeof MultiValueInput> = {
	component: MultiValueInput,
}

export default meta

type Story = StoryObj<typeof MultiValueInput>

export const mainStory: Story = {
	args: {
		onRemove: test.fn(),
		values: [
			{ value: "first", label: "Tag label" },
			{ value: "second", label: "Tag label" },
			{ value: "third", label: "Tag label" },
		],
		tagButtonTitle: "testButton",
		titleText: "Multi value input title",
		placeholder: "Placeholder text",
		helperText: "Some additional helper text",
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
		onRemove: test.fn(),
		values: [
			{ value: "first", label: "Tag label" },
			{ value: "second", label: "Tag label" },
			{ value: "third", label: "Error Tag label", invalid: true },
			{ value: "fourth", label: "Tag label" },
		],
		tagButtonTitle: "testButton",
		titleText: "Multi value input title",
		placeholder: "Placeholder text",
		invalid: true,
		invalidText: "Third value is invalid",
	},
}
