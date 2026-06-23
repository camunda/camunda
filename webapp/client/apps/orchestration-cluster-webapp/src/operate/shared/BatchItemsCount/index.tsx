/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tooltip} from '@carbon/react';
import {Checkmark, CircleDash, ErrorOutline, Pending} from '@carbon/react/icons';
import styled from 'styled-components';

const ItemGroup = styled.div`
	display: flex;
	align-items: center;
	gap: var(--cds-spacing-04);
`;

const Item = styled.span<{$color?: string}>`
	display: flex;
	align-items: center;
	gap: var(--cds-spacing-02);
	cursor: default;
	min-width: 3rem;
	color: ${({$color}) => $color ?? 'inherit'};
`;

const formatCount = (count: number): string =>
	Intl.NumberFormat('en', {notation: 'compact', maximumFractionDigits: 1}).format(count);

type Props = {
	totalCount: number;
	completedCount: number;
	failedCount: number;
};

const BatchItemsCount: React.FC<Props> = ({totalCount, completedCount, failedCount}) => {
	const pendingCount = totalCount - completedCount - failedCount;
	const hasAnyProgress = completedCount > 0 || failedCount > 0;

	if (!hasAnyProgress && pendingCount > 0) {
		const description = 'not started';
		return (
			<Tooltip description={description} align="bottom">
				<Item $color="var(--cds-status-gray)" aria-label={description}>
					<CircleDash aria-hidden="true" focusable="false" /> 0
				</Item>
			</Tooltip>
		);
	}

	const statusConfig = [
		{key: 'successful', count: completedCount, label: 'successful', Icon: Checkmark, color: 'var(--cds-status-green)'},
		{key: 'failed', count: failedCount, label: 'failed', Icon: ErrorOutline, color: 'var(--cds-status-red)'},
		{key: 'pending', count: pendingCount, label: 'pending', Icon: Pending, color: 'var(--cds-status-gray)'},
	];

	return (
		<ItemGroup>
			{statusConfig
				.filter(({count}) => count > 0)
				.map(({key, count, label, Icon, color}) => {
					const description = `${count.toLocaleString()} ${label}`;
					return (
						<Tooltip key={key} description={description} align="bottom">
							<Item role="status" aria-label={description}>
								<Icon color={color} aria-hidden="true" focusable="false" />
								{formatCount(count)}
							</Item>
						</Tooltip>
					);
				})}
		</ItemGroup>
	);
};

export {BatchItemsCount};
