/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLayoutEffect, useRef, useState} from 'react';
import {getDrdPanelWidth, persistDrdPanelWidth} from './useDrdPanelState';

const MIN_WIDTH = 540;
const MAX_WIDTH_RATIO = 3 / 5;
const KEYBOARD_STEP = 10;

const getMaxWidth = () => Math.floor(document.body.clientWidth * MAX_WIDTH_RATIO);

const KEY_TO_WIDTH: Record<string, (currentWidth: number) => number> = {
	ArrowLeft: (currentWidth) => currentWidth + KEYBOARD_STEP,
	ArrowRight: (currentWidth) => currentWidth - KEYBOARD_STEP,
	Home: () => MIN_WIDTH,
	End: () => document.body.clientWidth,
};

const useDrdPanelResize = () => {
	const panelRef = useRef<HTMLElement>(null);
	const handleRef = useRef<HTMLDivElement>(null);
	const [maxWidth, setMaxWidth] = useState(getMaxWidth);
	const [width, setCurrentWidth] = useState(() => Math.min(MIN_WIDTH, maxWidth));

	useLayoutEffect(() => {
		const panel = panelRef.current;
		const handle = handleRef.current;
		if (panel === null || handle === null) {
			return;
		}

		const state = {
			preferredWidth: getDrdPanelWidth() ?? MIN_WIDTH,
			startX: 0,
			startWidth: 0,
			lastX: 0,
			maxDragWidth: 0,
			previousCursor: '',
			isResizing: false,
			draggedWidth: null as number | null,
		};
		const setWidth = (requestedWidth: number) => {
			const nextMaxWidth = getMaxWidth();
			const nextWidth = Math.min(Math.max(requestedWidth, MIN_WIDTH), nextMaxWidth);
			panel.style.width = `${nextWidth}px`;
			setMaxWidth(nextMaxWidth);
			setCurrentWidth(nextWidth);
			return nextWidth;
		};
		const updatePreferredWidth = (requestedWidth: number) => {
			const currentWidth = panel.getBoundingClientRect().width;
			const nextWidth = setWidth(requestedWidth);
			if (nextWidth === currentWidth) {
				return false;
			}
			state.preferredWidth = nextWidth;
			return true;
		};
		const persistWidth = () => {
			persistDrdPanelWidth(state.preferredWidth);
		};
		setWidth(state.preferredWidth);

		const onMouseMove = (event: MouseEvent) => {
			if ((event.buttons & 1) === 0) {
				endResize();
				return;
			}
			if (event.clientX === state.lastX) {
				return;
			}
			state.lastX = event.clientX;
			const previousWidth = panel.getBoundingClientRect().width;
			const requestedWidth = state.startWidth - (event.clientX - state.startX);
			const nextWidth = setWidth(requestedWidth);
			state.maxDragWidth = Math.max(state.maxDragWidth, getMaxWidth());
			if (event.clientX === state.startX) {
				state.draggedWidth = null;
			} else if (nextWidth !== previousWidth || state.draggedWidth !== null) {
				state.draggedWidth = Math.min(Math.max(requestedWidth, MIN_WIDTH), state.maxDragWidth);
			}
		};
		const endResize = () => {
			if (!state.isResizing) {
				return;
			}
			state.isResizing = false;
			window.removeEventListener('mousemove', onMouseMove);
			window.removeEventListener('mouseup', stopResize);
			window.removeEventListener('blur', endResize);
			panel.classList.remove('resizing');
			document.body.style.cursor = state.previousCursor;
			if (state.draggedWidth !== null && state.draggedWidth !== state.startWidth) {
				state.preferredWidth = state.draggedWidth;
				persistWidth();
			}
			state.draggedWidth = null;
		};
		const stopResize = (event: MouseEvent) => {
			if (event.button === 0) {
				endResize();
			}
		};
		const startResize = (event: MouseEvent) => {
			if (event.button !== 0) {
				return;
			}
			event.preventDefault();
			state.startX = event.clientX;
			state.lastX = state.startX;
			state.startWidth = panel.getBoundingClientRect().width;
			state.maxDragWidth = getMaxWidth();
			state.previousCursor = document.body.style.cursor;
			state.isResizing = true;
			state.draggedWidth = null;
			document.body.style.cursor = 'ew-resize';
			panel.classList.add('resizing');
			window.addEventListener('mousemove', onMouseMove);
			window.addEventListener('mouseup', stopResize);
			window.addEventListener('blur', endResize);
		};
		const onWindowResize = () => {
			setWidth(state.draggedWidth ?? state.preferredWidth);
		};
		const onKeyDown = (event: KeyboardEvent) => {
			const getNextWidth = KEY_TO_WIDTH[event.key];
			if (getNextWidth === undefined) {
				return;
			}
			event.preventDefault();
			if (updatePreferredWidth(getNextWidth(panel.getBoundingClientRect().width))) {
				persistWidth();
			}
		};

		handle.addEventListener('mousedown', startResize);
		handle.addEventListener('keydown', onKeyDown);
		window.addEventListener('resize', onWindowResize);
		return () => {
			handle.removeEventListener('mousedown', startResize);
			handle.removeEventListener('keydown', onKeyDown);
			window.removeEventListener('resize', onWindowResize);
			window.removeEventListener('mousemove', onMouseMove);
			window.removeEventListener('mouseup', stopResize);
			window.removeEventListener('blur', endResize);
			if (state.isResizing) {
				document.body.style.cursor = state.previousCursor;
			}
		};
	}, []);

	return {
		panelRef,
		handleRef,
		width,
		maxWidth,
		minWidth: Math.min(MIN_WIDTH, maxWidth),
	};
};

export {useDrdPanelResize};
