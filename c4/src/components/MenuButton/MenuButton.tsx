/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
	fullScreenTarget?: Element | null
}

export default forwardRef<HTMLDivElement, MenuButtonProps>(function MenuButton(
	{
		label,
		menuLabel,
		size = "lg",
		kind,
		children,
		className,
		disabled,
		iconDescription,
		hasIconOnly,
		fullScreenTarget,
	},
	forwardedRef,
) {
	const menuRef = useRef<HTMLDivElement>(null)
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
					target={fullScreenTarget || undefined}
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
