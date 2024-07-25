/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	forwardRef,
	ReactNode,
	useRef,
	useState,
	ForwardedRef,
	useEffect,
	useCallback,
	ComponentPropsWithoutRef,
} from "react"
import classnames from "classnames"
import { Menu, usePrefix } from "@carbon/react"
import ListBox from "@carbon/react/lib/components/ListBox"
import { useMergedRefs } from "@carbon/react/lib/internal/useMergedRefs"

import { useId, useAttachedMenu } from "../../hooks"

import "./MenuDropdown.scss"

export interface MenuDropdownProps extends ComponentPropsWithoutRef<"div"> {
	size?: "sm" | "md" | "lg"
	children: ReactNode
	className?: string
	disabled?: boolean
	label: string
	invalid?: boolean
	invalidText?: string
	menuTarget?: HTMLElement | null
}

export default forwardRef(function MenuDropdown(
	{
		children,
		className,
		disabled,
		label,
		size = "sm",
		invalid,
		invalidText,
		menuTarget,
		...rest
	}: MenuDropdownProps,
	forwardRef: ForwardedRef<HTMLDivElement>,
) {
	const prefix = usePrefix()
	const triggerRef = useRef<HTMLDivElement>(null)
	const menuRef = useRef<HTMLUListElement>(null)
	const buttonRef = useRef<HTMLButtonElement>(null)
	const [width, setWidth] = useState(0)
	const id = useId()

	const ref = useMergedRefs<HTMLDivElement>([forwardRef, triggerRef])
	const {
		open,
		x,
		y,
		handleClick: hookOnClick,
		handleMousedown,
		handleClose,
	} = useAttachedMenu(triggerRef)

	const handleScroll = useCallback(
		(evt: Event) => {
			if ((evt.target as HTMLElement | null)?.contains(buttonRef.current)) {
				handleClose()
			}
		},
		[handleClose],
	)

	useEffect(() => {
		if (open) {
			document.body.addEventListener("scroll", handleScroll, true)
		} else {
			document.body.removeEventListener("scroll", handleScroll, true)
		}

		return () => {
			document.body.removeEventListener("scroll", handleScroll, true)
		}
	}, [open])

	function handleClick() {
		if (triggerRef.current) {
			const { width } = triggerRef.current.getBoundingClientRect()
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
		<div
			{...rest}
			aria-owns={open ? id : undefined}
			className={classnames(className, "MenuDropdown")}
		>
			<ListBox
				isOpen={open}
				size={size}
				disabled={disabled}
				invalid={invalid}
				invalidText={invalidText}
				ref={ref}
			>
				<button
					type="button"
					ref={buttonRef}
					className={`${prefix}--list-box__field`}
					disabled={disabled}
					onClick={handleClick}
					onMouseDown={handleMousedown}
					aria-haspopup
					aria-expanded={open}
					aria-controls={open ? id : undefined}
				>
					<span className={`${prefix}--list-box__label`}>{label}</span>
					<ListBox.MenuIcon isOpen={open} />
				</button>
			</ListBox>
			<Menu
				id={id}
				target={menuTarget || undefined}
				className="MenuDropdownMenu"
				ref={menuRef}
				label={label}
				size={size}
				open={open}
				onClose={handleClose}
				onOpen={handleOpen}
				x={x}
				y={y}
			>
				{children}
			</Menu>
		</div>
	)
})
