/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {InlineLoading} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import type {IncidentProcessInstanceStatisticsByError} from '@camunda/camunda-api-zod-schemas/8.10';
import {InstancesBar} from '#/operate/components/InstancesBar/InstancesBar';
import {EmptyState} from '#/operate/components/EmptyState/EmptyState';
import emptyStateIconUrl from '#/operate/assets/empty-state-process-instances-by-name.svg';
import {useIncidentsByError, PAGE_SIZE} from './useIncidentsByError';
import {ScrollableList, Row, LoadingRow} from './styled';

const ROW_HEIGHT = 64;

function IncidentsByError() {
	const {t} = useTranslation();
	const {
		data,
		fetchNextPage,
		fetchPreviousPage,
		hasNextPage,
		hasPreviousPage,
		isFetchingNextPage,
		isFetchingPreviousPage,
	} = useIncidentsByError();

	const items = useMemo(() => data.pages.flatMap((page) => page.items), [data]);
	const totalItems = data.pages[0]?.page.totalItems ?? 0;

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

	if (totalItems === 0) {
		return (
			<EmptyState
				icon={<img src={emptyStateIconUrl} alt={t('operate.dashboard.healthyProcessesHeading')} />}
				heading={t('operate.dashboard.healthyProcessesHeading')}
				description={t('operate.dashboard.healthyProcessesDescription')}
			/>
		);
	}

	return (
		<ScrollableList onScroll={handleScroll} data-testid="incidents-by-error-list">
			{isFetchingPreviousPage && (
				<LoadingRow>
					<InlineLoading />
				</LoadingRow>
			)}
			{items.map((item: IncidentProcessInstanceStatisticsByError) => (
				<Row key={item.errorHashCode}>
					<InstancesBar
						label={{type: 'incident', size: 'small', text: item.errorMessage}}
						incidentsCount={item.activeInstancesWithErrorCount}
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

export {IncidentsByError};
