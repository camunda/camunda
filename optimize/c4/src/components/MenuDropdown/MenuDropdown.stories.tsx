import type { Meta, StoryObj } from "@storybook/react"
import { MenuItemSelectable } from "@carbon/react"
import { within, expect, userEvent } from "@storybook/test"

import MenuDropdown from "./MenuDropdown"

const meta: Meta<typeof MenuDropdown> = {
	component: MenuDropdown,
	argTypes: {
		size: {
			options: ["sm", "md", "lg"],
			control: { type: "select" },
		},
		disabled: { control: "boolean" },
		invalid: { control: "boolean" },
	},
}

export default meta

type Story = StoryObj<typeof MenuDropdown>

export const mainStory: Story = {
	args: {
		children: [
			<MenuItemSelectable label="item1" />,
			<MenuItemSelectable label="item2" />,
			// @ts-ignore
			<MenuItemSelectable label="item3" selected>
				<MenuItemSelectable label="sub item" selected />
			</MenuItemSelectable>,
		],
		label: "Dropdown with submenu",
		size: "sm",
		disabled: false,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement)

		const triggerBtn = canvas.getByText("Dropdown with submenu")
		expect(triggerBtn).not.toBeNull()
	},
}

export const invalidInput: Story = {
	args: {
		children: [
			<MenuItemSelectable label="item1" />,
			<MenuItemSelectable label="item2" />,
			// @ts-ignore
			<MenuItemSelectable label="item3" selected>
				<MenuItemSelectable label="sub item" selected />
				{Array.from(Array(30).keys()).map((id) => (
					<MenuItemSelectable key={id} label={"sub item" + id} />
				))}
			</MenuItemSelectable>,
		],
		label: "Dropdown with submenu",
		size: "sm",
		disabled: false,
		invalid: true,
		invalidText: "Something went wrong",
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement)
		const triggerBtn = canvas.getByText("Dropdown with submenu")
		await triggerBtn.click()
		const submenu = within(document.querySelector(".MenuDropdownMenu")!)

		expect(submenu.getByText("item3")).not.toBeNull()
		userEvent.hover(submenu.getByText("item3"))
		expect(submenu.getByText("sub item")).not.toBeNull()
	},
}

export const tooManyOptions: Story = {
	args: {
		children: [
			<MenuItemSelectable label="item1" />,
			<MenuItemSelectable label="item2" />,
			// @ts-ignore
			<MenuItemSelectable label="item3" selected>
				<MenuItemSelectable label="sub item" selected />
				{Array.from(Array(30).keys()).map((id) => (
					<MenuItemSelectable key={id} label={"sub item" + id} />
				))}
			</MenuItemSelectable>,
		],
		label: "Dropdown with submenu",
		size: "sm",
		disabled: false,
	},
	play: async ({ canvasElement }) => {
		const canvas = within(canvasElement)
		const triggerBtn = canvas.getByText("Dropdown with submenu")
		await triggerBtn.click()
		const submenu = within(document.querySelector(".MenuDropdownMenu")!)

		expect(submenu.getByText("item3")).not.toBeNull()
		userEvent.hover(submenu.getByText("item3"))
		expect(submenu.getByText("sub item")).not.toBeNull()
	},
}
