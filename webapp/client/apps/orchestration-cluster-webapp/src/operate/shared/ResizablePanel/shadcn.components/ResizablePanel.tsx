/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
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
		"relative h-full! w-0! cursor-col-resize! p-0! after:absolute after:right-0 after:h-full after:w-px after:content-['']",
};

// Vertical keeps the Carbon-derived idle/active guide line (subtle at rest,
// primary color while dragging) — there's no AppSidebar equivalent to match
// for vertical resizing. Horizontal instead mirrors AppSidebar's panel edge,
// which keeps a persistent light `var(--border)` line at rest (its resize
// handle is a separate, invisible-until-dragged overlay on top of that line —
// not a replacement for it), then shows the primary color while dragging.
// Uses the `[var(--border)]` arbitrary-value form rather than the bare
// `border` utility name: Tailwind only pre-generates `after:bg-*` compound
// variants it finds as literal class strings in scanned source, and "border"
// isn't registered as a theme color in this app's own Tailwind build (it's
// only available as a raw CSS custom property from the imported
// @camunda/design-system stylesheet).
const GUTTER_LINE_COLOR: Record<SplitDirection, {idle: string; active: string}> = {
	[SplitDirection.Vertical]: {
		idle: 'after:bg-[var(--neutral-border-subtle)]',
		active: 'after:bg-[var(--primary-action-default)]',
	},
	[SplitDirection.Horizontal]: {
		idle: 'after:bg-[var(--border)]',
		active: 'after:bg-[var(--primary-action-default)]',
	},
};

const DRAGGER_CLASSES: Record<SplitDirection, string> = {
	[SplitDirection.Vertical]: 'h-2.5! w-full!',
	[SplitDirection.Horizontal]: 'h-full! w-2.5!',
};

const PANEL_CURSOR_CLASSES: Record<SplitDirection, string> = {
	[SplitDirection.Vertical]: 'cursor-ns-resize',
	[SplitDirection.Horizontal]: 'cursor-col-resize',
};

const BODY_CURSOR_CLASSES: Record<SplitDirection, string> = {
	[SplitDirection.Vertical]: 'cursor-ns-resize',
	[SplitDirection.Horizontal]: 'cursor-col-resize',
};

const ResizablePanel: React.FC<Props> = ({children, direction, minHeights, minWidths, panelId}) => {
	const [isResizing, setIsResizing] = useState(false);
	const storedSizes = getStateLocally('operate.panelStates')?.[panelId];
	const initialSizes = Array.isArray(storedSizes) ? storedSizes : [50, 50];
	const panelCursorClassName = isResizing ? PANEL_CURSOR_CLASSES[direction] : '';

	// Managed in an effect (rather than inline in the resize callbacks) so the
	// cleanup also runs if the panel unmounts mid-drag (e.g. route navigation),
	// instead of leaving the body stuck with the resize cursor.
	useEffect(() => {
		if (!isResizing) {
			return;
		}

		document.body.classList.add(BODY_CURSOR_CLASSES[direction]);

		return () => {
			document.body.classList.remove(BODY_CURSOR_CLASSES[direction]);
		};
	}, [isResizing, direction]);

	return (
		<Splitter
			classes={[panelCursorClassName, panelCursorClassName]}
			direction={direction}
			minHeights={minHeights}
			minWidths={minWidths}
			initialSizes={initialSizes}
			gutterClassName={cn(
				GUTTER_BASE_CLASSES[direction],
				isResizing ? GUTTER_LINE_COLOR[direction].active : GUTTER_LINE_COLOR[direction].idle,
			)}
			draggerClassName={DRAGGER_CLASSES[direction]}
			onResizeStarted={() => {
				setIsResizing(true);
			}}
			onResizeFinished={(_, newSizes) => {
				setIsResizing(false);
				storeStateLocally('operate.panelStates', {
					...(getStateLocally('operate.panelStates') ?? {}),
					[panelId]: newSizes,
				});
			}}
		>
			{children}
		</Splitter>
	);
};

export {ResizablePanel, SplitDirection};
