/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {
	Ban,
	CircleCheck,
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
	Icon: typeof CircleCheck;
	className: string;
};

const STATE_CONFIG: Record<BatchOperationState | BatchOperationItemState, Config> = {
	COMPLETED: {Icon: CircleCheck, className: 'text-success-foreground-subtle'},
	ACTIVE: {Icon: LoaderCircle, className: 'text-info-foreground-strong'},
	SUSPENDED: {Icon: CirclePause, className: 'text-neutral-foreground-subtle'},
	CANCELED: {Icon: Ban, className: 'text-danger-foreground-subtle'},
	FAILED: {Icon: CircleX, className: 'text-danger-foreground-subtle'},
	CREATED: {Icon: CircleDashed, className: 'text-neutral-foreground-subtle'},
	PARTIALLY_COMPLETED: {Icon: CircleDotDashed, className: 'text-info-foreground-strong'},
	SKIPPED: {Icon: SkipForward, className: 'text-neutral-foreground-subtle'},
};

type Props = {
	state: BatchOperationState | BatchOperationItemState;
	ariaLabelPrefix?: string;
};

const BatchStateIndicator: React.FC<Props> = ({state, ariaLabelPrefix}) => {
	const {t} = useTranslation();
	const {Icon, className} = STATE_CONFIG[state];
	const stateLabels: Record<BatchOperationState | BatchOperationItemState, string> = {
		COMPLETED: t('operate.shared.batchStateIndicator.state.completed'),
		ACTIVE: t('operate.shared.batchStateIndicator.state.active'),
		SUSPENDED: t('operate.shared.batchStateIndicator.state.suspended'),
		CANCELED: t('operate.shared.batchStateIndicator.state.canceled'),
		FAILED: t('operate.shared.batchStateIndicator.state.failed'),
		CREATED: t('operate.shared.batchStateIndicator.state.created'),
		PARTIALLY_COMPLETED: t('operate.shared.batchStateIndicator.state.partiallyCompleted'),
		SKIPPED: t('operate.shared.batchStateIndicator.state.skipped'),
	};
	const label = stateLabels[state];
	const resolvedAriaLabelPrefix = ariaLabelPrefix ?? t('operate.shared.batchStateIndicator.ariaLabelPrefix');

	return (
		<div role="status" aria-label={`${resolvedAriaLabelPrefix}: ${label}`} className="flex items-center">
			<Icon aria-hidden="true" focusable="false" className={cn('mr-3 h-4 w-4 shrink-0', className)} />
			{label}
		</div>
	);
};

export {BatchStateIndicator};
