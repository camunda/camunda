/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useState} from 'react';
import {Tooltip, TooltipContent, TooltipProvider, TooltipTrigger} from '@camunda/design-system';
import {Popover, PopoverContent} from '#/shared/design-system-compat';
import {featureFlags} from '#/shared/feature-flags';
import {cn} from '#/shared/cn';
import styles from './LabelWithPopover.module.scss';

type Align = 'top-start' | 'top-end';

type Props = {
	title: string;
	popoverContent: React.ReactNode;
	children: React.ReactNode;
	align: Align;
};

// This is genuinely hover-to-show-info content — a tooltip, not a click-driven
// popover. The DS's real Tooltip primitives are used directly here (bypassing
// the compat layer, which has no working equivalent for this — see
// LabelWithPopoverLegacy below): they handle hover open/close natively, position
// relative to the actual wrapped element via TooltipTrigger's `asChild`, and
// size to content by default (`w-fit` on TooltipContent). Bypassing the compat
// layer here is exactly why this needs its own explicit feature-flag branch
// below, unlike everything routed through #/shared/design-system-compat.
const LabelWithPopoverDS: React.FC<Props> = ({title, popoverContent, children, align}) => (
	<TooltipProvider>
		<Tooltip>
			<TooltipTrigger asChild>
				<span className={cn(styles.label, styles.labelPrimary, styles.labelDS)} title={title}>
					{children}
				</span>
			</TooltipTrigger>
			{/* No className override: default DS TooltipContent padding/font-size
			    (px-2 py-1 text-xs) matches the sidebar's collapsed-rail tooltips. */}
			<TooltipContent data-testid="label-with-popover-content" side="top" align={align === 'top-end' ? 'end' : 'start'}>
				{popoverContent}
			</TooltipContent>
		</Tooltip>
	</TooltipProvider>
);

// Original Carbon-Popover-based implementation, unchanged from before this
// session's Tooltip rewrite. `Popover` here is the compat export — with the
// flag off it resolves to Carbon's real Popover (a real DOM component, not a
// context-only Radix Root), so onMouseEnter/onMouseLeave on it work fine;
// this bug was specific to the DS-compat Popover, never present in old-UI.
const LabelWithPopoverLegacy: React.FC<Props> = ({title, popoverContent, children, align}) => {
	const [popOverOpen, setPopOverOpen] = useState(false);
	const onMouseEnter = useCallback(() => setPopOverOpen(true), []);
	const onMouseLeave = useCallback(() => setPopOverOpen(false), []);
	return (
		<Popover open={popOverOpen} align={align} caret onMouseEnter={onMouseEnter} onMouseLeave={onMouseLeave}>
			<span className={cn(styles.label, styles.labelPrimary)} title={title}>
				{children}
			</span>
			<PopoverContent className={styles.popoverContent}>{popoverContent}</PopoverContent>
		</Popover>
	);
};

const LabelWithPopover: React.FC<Props> = (props) =>
	featureFlags.dsTasklistUI ? <LabelWithPopoverDS {...props} /> : <LabelWithPopoverLegacy {...props} />;

export {LabelWithPopover};
export type {Align};
