import { MouseEvent, RefObject, useState } from "react"

export function useAttachedMenu(anchor: RefObject<Element>) {
	const [open, setOpen] = useState(false)
	const [position, setPosition] = useState([
		[-1, -1],
		[-1, -1],
	])

	function openMenu() {
		const anchorEl = anchor?.current

		if (anchorEl) {
			const { left, top, right, bottom } = anchorEl.getBoundingClientRect()

			setPosition([
				[left, right],
				[top, bottom],
			])
		}

		setOpen(true)
	}

	function closeMenu() {
		setOpen(false)
	}

	function handleClick() {
		if (open) {
			closeMenu()
		} else {
			openMenu()
		}
	}

	function handleMousedown(e: MouseEvent<HTMLButtonElement>) {
		e.preventDefault()
	}

	return {
		open,
		x: position[0],
		y: position[1],
		handleClick,
		handleMousedown,
		handleClose: closeMenu,
	}
}
