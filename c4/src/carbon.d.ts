/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
	FC,
	PropsWithoutRef,
	PropsWithChildren,
	RefAttributes,
	ReactNode,
	ComponentProps,
} from "react"

declare module "@carbon/react" {
	export type ForwardRefProps<T, P = {}> = PropsWithoutRef<
		PropsWithChildren<P>
	> &
		RefAttributes<T>

	interface MenuProps {
		id: string
		className?: string
		target?: Element | null
		label: ReactNode
		size: "xs" | "sm" | "md" | "lg"
		open: boolean
		onClose: () => void
		onOpen: () => void
		x: number[]
		y: number[]
		target?: Element | null
	}

	function Menu(props: ForwardRefProps<HTMLElement, MenuProps>): JSX.Element

	function MenuItem(props: ForwardRefProps<HTMLElement, {}>): JSX.Element
	function MenuItemSelectable(
		props: ForwardRefProps<HTMLElement, { label: string; selected?: boolean }>,
	): JSX.Element

	interface MenuButtonProps extends ComponentProps<"div"> {
		label: string
		size?: "sm" | "md" | "lg"
		disabled?: boolean
		kind: "primary" | "secondary" | "ghost"
	}

	function MenuButton(props: ForwardRefProps<HTMLDivElement, MenuButtonProps>)
}

declare module "react" {}
