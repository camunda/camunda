/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {InlineLoading} from '@carbon/react';
import type {ProcessDefinitionInstanceStatistics} from '@camunda/camunda-api-zod-schemas/8.10';
import {InstancesBar} from '#/operate/components/InstancesBar/InstancesBar';
import {useInstancesByProcess, PAGE_SIZE} from './useInstancesByProcess';
import {ScrollableList, Row, LoadingRow} from './styled';

const ROW_HEIGHT = 64;

function InstancesByProcess() {
	const {data, fetchNextPage, fetchPreviousPage, hasNextPage, hasPreviousPage, isFetchingNextPage, isFetchingPreviousPage} =
		useInstancesByProcess();

	const items = useMemo(() => data.pages.flatMap((page) => page.items), [data]);

	const handleScroll = useCallback(
		async (event: React.UIEvent<HTMLDivElement>) => {
			const target = event.target as HTMLDivElement;
			const atBottom = Math.floor(target.scrollHeight - target.clientHeight - target.scrollTop) <= 0;
			const atTop = target.scrollTop === 0;

			if (atBottom && hasNextPage && !isFetchingNextPage) {
				await fetchNextPage();
			} else if (atTop && hasPreviousPage && !isFetchingPreviousPage) {
				await fetchPreviousPage();
				target.scrollTop = PAGE_SIZE * ROW_HEIGHT;
			}
		},
		[hasNextPage, hasPreviousPage, isFetchingNextPage, isFetchingPreviousPage, fetchNextPage, fetchPreviousPage],
	);

	return (
		<ScrollableList onScroll={handleScroll} data-testid="instances-by-process-list">
			{isFetchingPreviousPage && (
				<LoadingRow>
					<InlineLoading />
				</LoadingRow>
			)}
			{items.map((item: ProcessDefinitionInstanceStatistics) => (
				<Row key={`${item.processDefinitionId}:${item.tenantId}`}>
					<InstancesBar
						label={{
							type: 'process',
							size: 'medium',
							text: item.latestProcessDefinitionName || item.processDefinitionId,
						}}
						activeInstancesCount={item.activeInstancesWithoutIncidentCount}
						incidentsCount={item.activeInstancesWithIncidentCount}
						size="medium"
					/>
				</Row>
			))}
			{isFetchingNextPage && (
				<LoadingRow>
					<InlineLoading />
				</LoadingRow>
			)}
		</ScrollableList>
	);
}

export {InstancesByProcess};
