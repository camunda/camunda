/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Badge, Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
import {Timer} from '@camunda/design-system/icons';

// Mirrors Carbon's `PopoverAlignment` (both the current `-start`/`-end` and
// the deprecated `-left`/`-right`/`-top`/`-bottom` values), since consumers
// of this component still pass the deprecated variants (e.g. `bottom-right`).
type Align =
	| 'top'
	| 'top-start'
	| 'top-end'
	| 'top-left'
	| 'top-right'
	| 'bottom'
	| 'bottom-start'
	| 'bottom-end'
	| 'bottom-left'
	| 'bottom-right'
	| 'left'
	| 'left-start'
	| 'left-end'
	| 'left-bottom'
	| 'left-top'
	| 'right'
	| 'right-start'
	| 'right-end'
	| 'right-bottom'
	| 'right-top';

type Props = {
	label: string;
	description: string;
	align?: Align;
	className?: string;
};

const ALIGN_TO_RADIX: Record<Align, {side: 'top' | 'bottom' | 'left' | 'right'; align: 'start' | 'center' | 'end'}> = {
	top: {side: 'top', align: 'center'},
	'top-start': {side: 'top', align: 'start'},
	'top-end': {side: 'top', align: 'end'},
	'top-left': {side: 'top', align: 'start'},
	'top-right': {side: 'top', align: 'end'},
	bottom: {side: 'bottom', align: 'center'},
	'bottom-start': {side: 'bottom', align: 'start'},
	'bottom-end': {side: 'bottom', align: 'end'},
	'bottom-left': {side: 'bottom', align: 'start'},
	'bottom-right': {side: 'bottom', align: 'end'},
	left: {side: 'left', align: 'center'},
	'left-start': {side: 'left', align: 'start'},
	'left-end': {side: 'left', align: 'end'},
	'left-bottom': {side: 'left', align: 'end'},
	'left-top': {side: 'left', align: 'start'},
	right: {side: 'right', align: 'center'},
	'right-start': {side: 'right', align: 'start'},
	'right-end': {side: 'right', align: 'end'},
	'right-bottom': {side: 'right', align: 'end'},
	'right-top': {side: 'right', align: 'start'},
};

const DrainingTag: React.FC<Props> = ({label, description, align = 'top', className}) => {
	const {side, align: radixAlign} = ALIGN_TO_RADIX[align];

	return (
		<Tooltip>
			<TooltipTrigger asChild>
				<Badge variant="danger" className={className} data-testid="draining-tag">
					<Timer data-icon="inline-start" aria-hidden="true" />
					{label}
				</Badge>
			</TooltipTrigger>
			<TooltipContent side={side} align={radixAlign}>
				{description}
			</TooltipContent>
		</Tooltip>
	);
};

export {DrainingTag};
