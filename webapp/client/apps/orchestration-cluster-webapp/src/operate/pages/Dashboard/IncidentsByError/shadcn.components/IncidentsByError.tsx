/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Suspense, useMemo} from 'react';
import {useInfiniteQuery} from '@tanstack/react-query';
import {Skeleton} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';
import {Link} from '@tanstack/react-router';
import type {IncidentProcessInstanceStatisticsByError} from '@camunda/camunda-api-zod-schemas/8.10';
import {ErrorBoundary} from 'react-error-boundary';
import {InstancesBar} from '#/operate/components/InstancesBar/shadcn.components/InstancesBar';
import {EmptyState} from '#/operate/components/EmptyState/shadcn.components/EmptyState';
import emptyStateIconUrl from '#/operate/assets/empty-state-process-instances-by-name.svg';
import {ExpandableList} from '../../shadcn.components/ExpandableList';
import {ExpandedRowErrorFallback} from '../../shadcn.components/ExpandedRowErrorFallback';
import {useDashboardScrollPagination} from '../../useDashboardScrollPagination';
import {incidentsByErrorInfiniteQuery, PAGE_SIZE} from '../incidentsByError.queries';
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
				content: (
					<Link
						to="/operate/processes"
						search={{
							errorMessage: item.errorMessage,
							incidents: true,
							active: false,
							completed: false,
							canceled: false,
							suspended: false,
						}}
						title={item.errorMessage}
						className="block py-1 no-underline"
					>
						<InstancesBar
							label={{type: 'incident', size: 'small', text: item.errorMessage}}
							incidentsCount={item.activeInstancesWithErrorCount}
							size="medium"
						/>
					</Link>
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
						<Suspense fallback={<Skeleton className="my-1 h-6 w-full" />}>
							<IncidentsByErrorDefinitions errorHashCode={item.errorHashCode} errorMessage={item.errorMessage} />
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
			header={t('operate.dashboard.incidentsByErrorTitle')}
			rows={rows}
			expandedContents={expandedContents}
			isFetchingNextPage={isFetchingNextPage}
			isFetchingPreviousPage={isFetchingPreviousPage}
			onScroll={onScroll}
		/>
	);
};

export {IncidentsByError};
