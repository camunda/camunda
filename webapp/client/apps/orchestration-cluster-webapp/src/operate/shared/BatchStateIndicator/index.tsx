/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	CheckmarkFilled,
	CircleDash,
	ErrorFilled,
	Incomplete,
	InProgress,
	MisuseOutline,
	PauseOutlineFilled,
	SkipForwardFilled,
	UnknownFilled,
	type CarbonIconType,
} from '@carbon/react/icons';
import {useTranslation} from 'react-i18next';
import type {BatchOperationItemState, BatchOperationState} from '@camunda/camunda-api-zod-schemas/8.11';
import {Container} from './styled';

type Config = {
	Icon: CarbonIconType;
	color: string;
};

const STATE_CONFIG: Record<BatchOperationState | BatchOperationItemState, Config> = {
	COMPLETED: {Icon: CheckmarkFilled, color: 'var(--cds-status-green)'},
	ACTIVE: {Icon: InProgress, color: 'var(--cds-status-blue)'},
	SUSPENDED: {Icon: PauseOutlineFilled, color: 'var(--cds-status-gray)'},
	CANCELED: {Icon: MisuseOutline, color: 'var(--cds-status-red)'},
	FAILED: {Icon: ErrorFilled, color: 'var(--cds-status-red)'},
	CREATED: {Icon: CircleDash, color: 'var(--cds-status-gray)'},
	PARTIALLY_COMPLETED: {Icon: Incomplete, color: 'var(--cds-status-blue)'},
	SKIPPED: {Icon: SkipForwardFilled, color: 'var(--cds-status-gray)'},
};

type Props = {
	state: BatchOperationState | BatchOperationItemState;
	ariaLabelPrefix?: string;
};

const BatchStateIndicator: React.FC<Props> = ({state, ariaLabelPrefix}) => {
	const {t} = useTranslation();
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
	const {Icon, color} = STATE_CONFIG[state] ?? {
		Icon: UnknownFilled,
		color: 'var(--cds-status-gray)',
	};
	const label = stateLabels[state] ?? state;
	const resolvedAriaLabelPrefix = ariaLabelPrefix ?? t('operate.shared.batchStateIndicator.ariaLabelPrefix');

	return (
		<Container role="status" aria-label={`${resolvedAriaLabelPrefix}: ${label}`}>
			<Icon style={{color}} aria-hidden="true" focusable="false" />
			{label}
		</Container>
	);
};

export {BatchStateIndicator};
