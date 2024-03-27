/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
	PropsWithoutRef,
	PropsWithChildren,
	RefAttributes,
	ComponentProps,
} from "react"

declare module "@carbon/react" {
	export type ForwardRefProps<T, P = {}> = PropsWithoutRef<
		PropsWithChildren<P>
	> &
		RefAttributes<T>

	interface MenuButtonProps extends ComponentProps<"div"> {
		label: string
		size?: "sm" | "md" | "lg"
		disabled?: boolean
		kind: "primary" | "secondary" | "ghost"
	}

	function MenuButton(props: ForwardRefProps<HTMLDivElement, MenuButtonProps>)
}

declare module "react" {}
