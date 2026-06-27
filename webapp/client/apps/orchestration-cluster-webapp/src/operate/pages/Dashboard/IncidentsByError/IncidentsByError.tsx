/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Suspense, useMemo} from 'react';
import {useInfiniteQuery} from '@tanstack/react-query';
import {InlineLoading} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import type {IncidentProcessInstanceStatisticsByError} from '@camunda/camunda-api-zod-schemas/8.10';
import {ErrorBoundary} from 'react-error-boundary';
import {tracking} from '#/shared/tracking';
import {InstancesBar} from '#/operate/components/InstancesBar/InstancesBar';
import {EmptyState} from '#/operate/components/EmptyState/EmptyState';
import emptyStateIconUrl from '#/operate/assets/empty-state-process-instances-by-name.svg';
import {ExpandableList} from '../ExpandableList';
import {ExpandedRowErrorFallback} from '../ExpandedRowErrorFallback';
import {useDashboardScrollPagination} from '../useDashboardScrollPagination';
import {LinkWrapper, LoadingRow} from '../styled';
import {incidentsByErrorInfiniteQuery, PAGE_SIZE} from './incidentsByError.queries';
import {IncidentsByErrorDefinitions} from './IncidentsByErrorDefinitions';

const IncidentsByError: React.FC = () => {
	const {t} = useTranslation();
	const {
		data,
		isPending,
		isError,
		fetchNextPage,
		fetchPreviousPage,
		hasNextPage,
		hasPreviousPage,
		isFetchingNextPage,
		isFetchingPreviousPage,
	} = useInfiniteQuery({...incidentsByErrorInfiniteQuery(), refetchInterval: 5000});

	const items = useMemo(() => data?.pages.flatMap((page) => page.items) ?? [], [data]);
	const totalItems = data?.pages[0]?.page.totalItems ?? 0;

	const onScroll = useDashboardScrollPagination({
		pageSize: PAGE_SIZE,
		hasNextPage,
		hasPreviousPage,
		isFetchingNextPage,
		isFetchingPreviousPage,
		fetchNextPage,
		fetchPreviousPage,
	});

	const rows = useMemo(
		() =>
			items.map((item: IncidentProcessInstanceStatisticsByError) => ({
				id: String(item.errorHashCode),
				incident: (
					<LinkWrapper
						to="/"
						title={item.errorMessage}
						onClick={() => {
							tracking.track({
								eventName: 'operate:navigation',
								link: 'dashboard-process-incidents-by-error-message-all-processes',
							});
						}}
					>
						<InstancesBar
							label={{type: 'incident', size: 'small', text: item.errorMessage}}
							incidentsCount={item.activeInstancesWithErrorCount}
							size="medium"
						/>
					</LinkWrapper>
				),
			})),
		[items],
	);

	const expandedContents = useMemo(
		() =>
			items.reduce<Record<string, React.ReactElement<{tabIndex: number}>>>((accumulator, item) => {
				accumulator[String(item.errorHashCode)] = (
					<ErrorBoundary
						fallback={<ExpandedRowErrorFallback message={t('operate.dashboard.incidentDetailsFetchError')} />}
					>
						<Suspense
							fallback={
								<LoadingRow>
									<InlineLoading />
								</LoadingRow>
							}
						>
							<IncidentsByErrorDefinitions errorHashCode={item.errorHashCode} />
						</Suspense>
					</ErrorBoundary>
				);
				return accumulator;
			}, {}),
		[items, t],
	);

	const healthyEmptyState =
		!isPending && !isError && totalItems === 0 ? (
			<EmptyState
				icon={<img src={emptyStateIconUrl} alt={t('operate.dashboard.healthyProcessesHeading')} />}
				heading={t('operate.dashboard.healthyProcessesHeading')}
				description={t('operate.dashboard.healthyProcessesDescription')}
			/>
		) : undefined;

	return (
		<ExpandableList
			isPending={isPending}
			isError={isError}
			emptyState={healthyEmptyState}
			listTestId="incidents-by-error-list"
			dataTestId="incident-byError"
			header="incident"
			rows={rows}
			expandedContents={expandedContents}
			isFetchingNextPage={isFetchingNextPage}
			isFetchingPreviousPage={isFetchingPreviousPage}
			onScroll={onScroll}
		/>
	);
};

export {IncidentsByError};
