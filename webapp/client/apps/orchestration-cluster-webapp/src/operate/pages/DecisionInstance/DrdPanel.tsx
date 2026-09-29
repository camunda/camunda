/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useLayoutEffect, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {DrdPanelContainer, DrdPanelHandle, DrdPanelSection} from './DrdPanel.styled';
import {getDrdPanelWidth, persistDrdPanelWidth} from './useDrdPanelState';

const MIN_WIDTH = 540;
const MAX_WIDTH_RATIO = 3 / 5;

type Props = {
	children: React.ReactNode;
};

const DrdPanel: React.FC<Props> = ({children}) => {
	const {t} = useTranslation();
	const panelRef = useRef<HTMLElement>(null);
	const handleRef = useRef<HTMLDivElement>(null);
	const [maxWidth, setMaxWidth] = useState(() => Math.floor(document.body.clientWidth * MAX_WIDTH_RATIO));
	const [width, setCurrentWidth] = useState(() => Math.min(MIN_WIDTH, maxWidth));

	useLayoutEffect(() => {
		const panel = panelRef.current;
		const handle = handleRef.current;
		if (panel === null || handle === null) {
			return;
		}

		const setWidth = (width: number) => {
			const nextMaxWidth = Math.floor(document.body.clientWidth * MAX_WIDTH_RATIO);
			const nextWidth = Math.min(Math.max(width, MIN_WIDTH), nextMaxWidth);
			panel.style.width = `${nextWidth}px`;
			setMaxWidth(nextMaxWidth);
			setCurrentWidth(nextWidth);
		};
		const persistWidth = () => {
			persistDrdPanelWidth(panel.getBoundingClientRect().width);
		};
		setWidth(getDrdPanelWidth() ?? MIN_WIDTH);

		let startX = 0;
		let startWidth = 0;
		let previousCursor = '';
		let isResizing = false;

		const onMouseMove = (event: MouseEvent) => {
			setWidth(startWidth - (event.clientX - startX));
		};
		const stopResize = () => {
			if (!isResizing) {
				return;
			}
			isResizing = false;
			window.removeEventListener('mousemove', onMouseMove);
			window.removeEventListener('mouseup', stopResize);
			panel.classList.remove('resizing');
			document.body.style.cursor = previousCursor;
			persistWidth();
		};
		const startResize = (event: MouseEvent) => {
			event.preventDefault();
			startX = event.clientX;
			startWidth = panel.getBoundingClientRect().width;
			previousCursor = document.body.style.cursor;
			isResizing = true;
			document.body.style.cursor = 'ew-resize';
			panel.classList.add('resizing');
			window.addEventListener('mousemove', onMouseMove);
			window.addEventListener('mouseup', stopResize);
		};
		const onWindowResize = () => {
			setWidth(panel.getBoundingClientRect().width);
		};
		const onKeyDown = (event: KeyboardEvent) => {
			const currentWidth = panel.getBoundingClientRect().width;
			if (event.key === 'ArrowLeft') {
				setWidth(currentWidth + 10);
			} else if (event.key === 'ArrowRight') {
				setWidth(currentWidth - 10);
			} else if (event.key === 'Home') {
				setWidth(MIN_WIDTH);
			} else if (event.key === 'End') {
				setWidth(document.body.clientWidth);
			} else {
				return;
			}
			event.preventDefault();
			persistWidth();
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
			if (isResizing) {
				document.body.style.cursor = previousCursor;
			}
		};
	}, []);

	return (
		<DrdPanelContainer>
			<DrdPanelSection
				id="operate-decision-drd-panel"
				ref={panelRef}
				aria-label={t('operate.decisionInstance.drd.panelLabel')}
			>
				{children}
			</DrdPanelSection>
			<DrdPanelHandle
				ref={handleRef}
				role="separator"
				tabIndex={0}
				aria-orientation="vertical"
				aria-label={t('operate.decisionInstance.drd.resize')}
				aria-controls="operate-decision-drd-panel"
				aria-valuemin={Math.min(MIN_WIDTH, maxWidth)}
				aria-valuemax={maxWidth}
				aria-valuenow={width}
			/>
		</DrdPanelContainer>
	);
};

export {DrdPanel};
