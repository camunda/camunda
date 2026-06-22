/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Stack} from '@carbon/react';
import styled from 'styled-components';

const ItemGroup = styled.div`
	display: flex;
	align-items: center;
	gap: var(--cds-spacing-02);
`;

const Item = styled.span<{$color?: string}>`
	color: ${({$color}) => $color ?? 'inherit'};
	font-size: var(--cds-body-short-01-font-size);
`;

const fmt = new Intl.NumberFormat('en', {notation: 'compact'});

type Props = {
	totalCount: number;
	completedCount: number;
	failedCount: number;
};

const BatchItemsCount: React.FC<Props> = ({totalCount, completedCount, failedCount}) => {
	const pendingCount = Math.max(0, totalCount - completedCount - failedCount);

	return (
		<Stack as="span" orientation="horizontal" gap={3}>
			<ItemGroup title={`${completedCount} completed`}>
				<Item $color="var(--cds-support-success)">{fmt.format(completedCount)}</Item>
				<span>completed</span>
			</ItemGroup>
			<ItemGroup title={`${failedCount} failed`}>
				<Item $color="var(--cds-support-error)">{fmt.format(failedCount)}</Item>
				<span>failed</span>
			</ItemGroup>
			<ItemGroup title={`${pendingCount} pending`}>
				<Item>{fmt.format(pendingCount)}</Item>
				<span>pending</span>
			</ItemGroup>
		</Stack>
	);
};

export {BatchItemsCount};
