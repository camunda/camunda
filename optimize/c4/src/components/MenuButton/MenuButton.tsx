/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	ComponentPropsWithoutRef,
	ReactNode,
	forwardRef,
	useRef,
	useState,
} from "react"
import { Button, Tooltip, Menu, MenuButton as MB } from "@carbon/react"
import { ChevronDown } from "@carbon/icons-react"
import { useMergedRefs } from "@carbon/react/lib/internal/useMergedRefs"
import classnames from "classnames"

import { useId, useAttachedMenu } from "../../hooks"

import "./MenuButton.scss"

interface MenuButtonProps
	extends Omit<ComponentPropsWithoutRef<typeof MB>, "label"> {
	label: ReactNode
	menuLabel: string
	iconDescription?: string
	hasIconOnly?: boolean
	menuTarget?: HTMLElement
	ariaLabel?: string
}

export default forwardRef<HTMLDivElement, MenuButtonProps>(function MenuButton(
	{
		ariaLabel,
		label,
		menuLabel,
		size = "lg",
		kind,
		children,
		className,
		disabled,
		iconDescription,
		hasIconOnly,
		menuTarget,
	},
	forwardedRef,
) {
	const menuRef = useRef<HTMLUListElement>(null)
	const triggerRef = useRef<HTMLDivElement>(null)
	const ref = useMergedRefs<HTMLDivElement>([triggerRef, forwardedRef])
	const [width, setWidth] = useState(0)
	const {
		open,
		x,
		y,
		handleClick: hookOnClick,
		handleMousedown,
		handleClose,
	} = useAttachedMenu(triggerRef)
	const id = useId()

	function handleClick() {
		if (triggerRef.current) {
			const { width } = triggerRef.current?.getBoundingClientRect()
			setWidth(width)
			hookOnClick()
		}
	}

	function handleOpen() {
		if (menuRef.current) {
			menuRef.current.style.width = `${width}px`
			menuRef.current.style.maxWidth = "unset"
		}
	}

	function getButtonAriaLabel() {
		let buttonAriaLabel = ariaLabel
		if (typeof label === "string") {
			buttonAriaLabel = label
		} else if (hasIconOnly) {
			buttonAriaLabel = iconDescription
		} else if (!buttonAriaLabel) {
			buttonAriaLabel = menuLabel
		}
		return buttonAriaLabel
	}

	return (
		<Tooltip
			label={hasIconOnly ? iconDescription : undefined}
			className={classnames("MenuButton cds--icon-tooltip", className, {
				hasIconOnly,
			})}
			leaveDelayMs={50}
		>
			<div className="MenuButton" ref={ref}>
				<Button
					className={classnames("cds--menu-button__trigger", {
						"cds--menu-button__trigger--open": open,
					})}
					size={size}
					kind={kind}
					renderIcon={ChevronDown}
					iconDescription={hasIconOnly ? iconDescription : undefined}
					aria-label={getButtonAriaLabel()}
					disabled={disabled}
					aria-haspopup
					aria-expanded={open}
					onClick={handleClick}
					onMouseDown={handleMousedown}
					aria-controls={open ? id : undefined}
				>
					{label}
				</Button>
				<Menu
					ref={menuRef}
					target={menuTarget || undefined}
					id={id}
					label={menuLabel}
					open={open}
					onClose={handleClose}
					onOpen={handleOpen}
					x={x}
					y={y}
					size={size}
				>
					{children}
				</Menu>
			</div>
		</Tooltip>
	)
})
