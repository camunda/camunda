/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	Ban,
	CircleCheckBig,
	CircleDashed,
	CircleDotDashed,
	CirclePause,
	CircleX,
	LoaderCircle,
	SkipForward,
} from '@camunda/design-system/icons';
import type {BatchOperationItemState, BatchOperationState} from '@camunda/camunda-api-zod-schemas/8.11';
import {cn} from '#/shared/cn';

type Config = {
	Icon: typeof CircleCheckBig;
	className: string;
	label: string;
};

const STATE_CONFIG: Record<BatchOperationState | BatchOperationItemState, Config> = {
	COMPLETED: {Icon: CircleCheckBig, className: 'text-success-foreground-strong', label: 'Completed'},
	ACTIVE: {Icon: LoaderCircle, className: 'text-info-foreground-strong', label: 'Active'},
	SUSPENDED: {Icon: CirclePause, className: 'text-neutral-foreground-subtle', label: 'Suspended'},
	CANCELED: {Icon: Ban, className: 'text-danger-foreground-strong', label: 'Canceled'},
	FAILED: {Icon: CircleX, className: 'text-danger-foreground-strong', label: 'Failed'},
	CREATED: {Icon: CircleDashed, className: 'text-neutral-foreground-subtle', label: 'Created'},
	PARTIALLY_COMPLETED: {
		Icon: CircleDotDashed,
		className: 'text-info-foreground-strong',
		label: 'Partially completed',
	},
	SKIPPED: {Icon: SkipForward, className: 'text-neutral-foreground-subtle', label: 'Skipped'},
};

type Props = {
	state: BatchOperationState | BatchOperationItemState;
	ariaLabelPrefix?: string;
};

const BatchStateIndicator: React.FC<Props> = ({state, ariaLabelPrefix = 'Batch operation status'}) => {
	const {Icon, className, label} = STATE_CONFIG[state];

	return (
		<div role="status" aria-label={`${ariaLabelPrefix}: ${label}`} className="flex items-center">
			<Icon aria-hidden="true" focusable="false" className={cn('mr-3 h-4 w-4 shrink-0', className)} />
			{label}
		</div>
	);
};

export {BatchStateIndicator};
