import type { Meta, StoryObj } from "@storybook/react"
import { MenuItemSelectable } from "@carbon/react"
import { within, expect } from "@storybook/test"

import MenuButton from "./MenuButton"
import { Calendar } from "@carbon/icons-react"

const meta: Meta<typeof MenuButton> = {
	component: MenuButton,
	argTypes: {
		size: {
			options: ["sm", "md", "lg"],
			control: { type: "select" },
		},
		kind: {
			options: ["primary", "secondary", "ghost"],
			control: { type: "select" },
		},
		disabled: { control: "boolean" },
	},
}

export default meta

type Story = StoryObj<typeof MenuButton>

export const mainStory: Story = {
	args: {
		children: [
			<MenuItemSelectable key="item1" label="item1" />,
			<MenuItemSelectable key="item2" label="item2" />,
			// @ts-ignore
			<MenuItemSelectable key="item3" label="item3" selected>
				<MenuItemSelectable label="sub item" selected />
			</MenuItemSelectable>,
		],
		label: "Menu button label",
		menuLabel: "Menu label",
		size: "sm",
		disabled: false,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement)

		const triggerBtn = canvas.getByText("Menu button label")
		expect(triggerBtn).not.toBeNull()
	},
}

export const labelWithIcon: Story = {
	args: {
		children: [<MenuItemSelectable key="item1" label="item1" />],
		label: (
			<>
				<Calendar /> label
			</>
		),
		menuLabel: "Menu label",
		size: "sm",
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement)

		const triggerBtn = canvas.getByText("label")
		expect(triggerBtn).not.toBeNull()
	},
}

export const labelWithIconOnly: Story = {
	args: {
		children: [<MenuItemSelectable key="item1" label="item1" />],
		label: <Calendar />,
		menuLabel: "Menu label",
		hasIconOnly: true,
		iconDescription: "icon",
		size: "sm",
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement)

		const triggerBtn = canvas.getByRole("button")
		expect(triggerBtn).not.toBeNull()
	},
}
