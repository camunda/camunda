/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import Splitter, {SplitDirection} from '@devbookhq/splitter';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {cn} from '#/shared/cn';

type Props = {
	direction: SplitDirection.Vertical | SplitDirection.Horizontal;
	minHeights?: number[];
	minWidths?: number[];
	panelId: string;
	children: React.ReactNode;
};

// Base gutter styling, direction dependent: a 1px guide line drawn via the
// `after` pseudo-element along the edge the gutter sits on. The Splitter
// library applies its own padding/sizing by default, hence the `!` (Tailwind's
// important modifier) overrides, mirroring the Carbon original's `!important`.
const GUTTER_BASE_CLASSES: Record<SplitDirection, string> = {
	[SplitDirection.Vertical]:
		"relative h-0! w-full! cursor-ns-resize! p-0! after:absolute after:top-0 after:h-px after:w-full after:content-['']",
	[SplitDirection.Horizontal]:
		"relative h-full! w-0! cursor-ew-resize! p-0! after:absolute after:right-0 after:h-full after:w-px after:content-['']",
};

// The guide line is subtle while idle and switches to the interactive/primary
// color while the user is actively dragging, matching the Carbon original's
// `.nsResizing`/`.ewResizing` body-class-driven highlight.
const GUTTER_LINE_COLOR = {
	idle: 'after:bg-[var(--neutral-border-subtle)]',
	active: 'after:bg-[var(--primary-action-default)]',
};

const DRAGGER_CLASSES: Record<SplitDirection, string> = {
	[SplitDirection.Vertical]: 'h-2.5! w-full!',
	[SplitDirection.Horizontal]: 'h-full! w-2.5!',
};

const PANEL_CURSOR_CLASSES: Record<SplitDirection, string> = {
	[SplitDirection.Vertical]: 'cursor-ns-resize',
	[SplitDirection.Horizontal]: 'cursor-ew-resize',
};

const BODY_CURSOR_CLASSES: Record<SplitDirection, string> = {
	[SplitDirection.Vertical]: 'cursor-ns-resize',
	[SplitDirection.Horizontal]: 'cursor-ew-resize',
};

const ResizablePanel: React.FC<Props> = ({children, direction, minHeights, minWidths, panelId}) => {
	const [isResizing, setIsResizing] = useState(false);
	const storedSizes = getStateLocally('operate.panelStates')?.[panelId];
	const initialSizes = Array.isArray(storedSizes) ? storedSizes : [50, 50];
	const panelCursorClassName = isResizing ? PANEL_CURSOR_CLASSES[direction] : '';

	return (
		<Splitter
			classes={[panelCursorClassName, panelCursorClassName]}
			direction={direction}
			minHeights={minHeights}
			minWidths={minWidths}
			initialSizes={initialSizes}
			gutterClassName={cn(
				GUTTER_BASE_CLASSES[direction],
				isResizing ? GUTTER_LINE_COLOR.active : GUTTER_LINE_COLOR.idle,
			)}
			draggerClassName={DRAGGER_CLASSES[direction]}
			onResizeStarted={() => {
				setIsResizing(true);
				document.body.classList.add(BODY_CURSOR_CLASSES[direction]);
			}}
			onResizeFinished={(_, newSizes) => {
				setIsResizing(false);
				storeStateLocally('operate.panelStates', {
					...(getStateLocally('operate.panelStates') ?? {}),
					[panelId]: newSizes,
				});
				document.body.classList.remove(BODY_CURSOR_CLASSES[direction]);
			}}
		>
			{children}
		</Splitter>
	);
};

export {ResizablePanel, SplitDirection};
