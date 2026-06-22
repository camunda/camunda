/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CheckmarkFilled, CheckmarkOutline, CloseFilled, Pause, Pending, Renew, Warning} from '@carbon/react/icons';
import styled from 'styled-components';
import type {BatchOperationState} from '@camunda/camunda-api-zod-schemas/8.10';

const Container = styled.span<{$color: string}>`
	display: inline-flex;
	align-items: center;
	gap: var(--cds-spacing-02);
	color: ${({$color}) => $color};
`;

type Config = {
	icon: React.ReactNode;
	color: string;
	label: string;
};

const STATE_CONFIG: Record<BatchOperationState, Config> = {
	COMPLETED: {icon: <CheckmarkFilled size={16} />, color: 'var(--cds-support-success)', label: 'Completed'},
	PARTIALLY_COMPLETED: {
		icon: <CheckmarkOutline size={16} />,
		color: 'var(--cds-support-warning)',
		label: 'Partially completed',
	},
	ACTIVE: {icon: <Renew size={16} />, color: 'var(--cds-link-primary)', label: 'Active'},
	CREATED: {icon: <Pending size={16} />, color: 'var(--cds-link-primary)', label: 'Created'},
	SUSPENDED: {icon: <Pause size={16} />, color: 'var(--cds-support-warning)', label: 'Suspended'},
	FAILED: {icon: <Warning size={16} />, color: 'var(--cds-support-error)', label: 'Failed'},
	CANCELED: {icon: <CloseFilled size={16} />, color: 'var(--cds-text-secondary)', label: 'Canceled'},
};

type Props = {
	state: BatchOperationState;
};

const BatchStateIndicator: React.FC<Props> = ({state}) => {
	const {icon, color, label} = STATE_CONFIG[state];
	return (
		<Container $color={color}>
			{icon}
			<span>{label}</span>
		</Container>
	);
};

export {BatchStateIndicator};
