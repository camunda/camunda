/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
