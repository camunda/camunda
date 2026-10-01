/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useEffect, useRef, useState} from 'react';
import Splitter, {SplitDirection} from '@devbookhq/splitter';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {cn} from '#/shared/cn';

type FrameProps = {
	headerTitle: string;
	isVisible?: boolean;
};

type Props = {
	leftPanel?: React.ReactNode;
	topPanel: React.ReactNode;
	bottomPanel: React.ReactNode;
	additionalTopContent?: React.ReactNode;
	footer?: React.ReactNode;
	frame?: FrameProps;
	type: 'process' | 'decision' | 'migrate';
};

// Mirrors the Carbon original's `grid-template-rows` cases, expressed as Tailwind arbitrary values.
// 3rem is the footer's fixed height (Carbon's `--cds-spacing-09`); `auto` reserves space for
// `additionalTopContent`'s own height.
function getGridRows(hasAdditionalTopContent: boolean, hasFooter: boolean) {
	if (!hasAdditionalTopContent && !hasFooter) {
		return 'grid-rows-1';
	}
	if (!hasFooter) {
		return 'grid-rows-[auto_1fr]';
	}
	if (!hasAdditionalTopContent) {
		return 'grid-rows-[1fr_3rem]';
	}
	return 'grid-rows-[auto_1fr_3rem]';
}

const InstancesList: React.FC<Props> = ({
	leftPanel,
	topPanel,
	bottomPanel,
	additionalTopContent,
	footer,
	frame,
	type,
}) => {
	const [clientHeight, setClientHeight] = useState(0);
	const containerRef = useRef<HTMLDivElement | null>(null);

	useEffect(() => {
		setClientHeight(containerRef.current?.clientHeight ?? 0);
	}, []);

	const panelMinHeight = clientHeight / 4;
	const panelId = `${type}-instances-vertical-panel`;
	const storedSizes = getStateLocally('operate.panelStates')?.[panelId];
	const initialSizes = Array.isArray(storedSizes) ? storedSizes : [50, 50];

	const content = (
		<div
			data-testid="instances-list"
			className={cn(
				'relative box-border grid h-full overflow-auto pt-12',
				leftPanel !== undefined ? 'grid-cols-[auto_minmax(0,1fr)]' : 'grid-cols-1',
				getGridRows(additionalTopContent !== undefined, footer !== undefined),
			)}
		>
			{leftPanel}
			{additionalTopContent}
			<div ref={containerRef} className="overflow-auto">
				<Splitter
					classes={['VerticalPanel', 'VerticalPanel']}
					direction={SplitDirection.Vertical}
					minHeights={[panelMinHeight, panelMinHeight]}
					initialSizes={initialSizes}
					gutterClassName="h-0! w-full cursor-ns-resize border-t border-neutral-border-subtle p-0!"
					draggerClassName="h-2.5! w-full!"
					onResizeStarted={() => {
						document.body.style.cursor = 'ns-resize';
					}}
					onResizeFinished={(_, newSizes) => {
						storeStateLocally('operate.panelStates', {
							...(getStateLocally('operate.panelStates') ?? {}),
							[panelId]: newSizes,
						});
						document.body.style.cursor = '';
					}}
				>
					{topPanel}
					{bottomPanel}
				</Splitter>
			</div>
			{footer}
		</div>
	);

	if (frame === undefined) {
		return content;
	}

	const {isVisible = true, headerTitle} = frame;

	return (
		<div
			className={cn(
				'h-full',
				isVisible && 'grid grid-rows-[2rem_1fr] border-4 border-t-0 border-neutral-border-subtle',
			)}
			data-testid="frame-container"
		>
			{isVisible && (
				<div className="flex items-center bg-neutral-background-strong pl-5 text-sm font-bold">{headerTitle}</div>
			)}
			{content}
		</div>
	);
};

export {InstancesList};
