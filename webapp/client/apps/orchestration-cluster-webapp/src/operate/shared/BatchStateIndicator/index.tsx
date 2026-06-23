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
	type CarbonIconType,
} from '@carbon/react/icons';
import type {BatchOperationState} from '@camunda/camunda-api-zod-schemas/8.10';
import {Container} from './styled';

type Config = {
	Icon: CarbonIconType;
	color: string;
	label: string;
};

const STATE_CONFIG: Record<BatchOperationState, Config> = {
	COMPLETED: {Icon: CheckmarkFilled, color: 'var(--cds-status-green)', label: 'Completed'},
	ACTIVE: {Icon: InProgress, color: 'var(--cds-status-blue)', label: 'Active'},
	SUSPENDED: {Icon: PauseOutlineFilled, color: 'var(--cds-status-gray)', label: 'Suspended'},
	CANCELED: {Icon: MisuseOutline, color: 'var(--cds-status-red)', label: 'Canceled'},
	FAILED: {Icon: ErrorFilled, color: 'var(--cds-status-red)', label: 'Failed'},
	CREATED: {Icon: CircleDash, color: 'var(--cds-status-gray)', label: 'Created'},
	PARTIALLY_COMPLETED: {Icon: Incomplete, color: 'var(--cds-status-blue)', label: 'Partially completed'},
};

type Props = {
	state: BatchOperationState;
};

const BatchStateIndicator: React.FC<Props> = ({state}) => {
	const {Icon, color, label} = STATE_CONFIG[state];
	return (
		<Container role="status" aria-label={`Batch operation status: ${label}`}>
			<Icon style={{color}} aria-hidden="true" focusable="false" />
			{label}
		</Container>
	);
};

export {BatchStateIndicator};
