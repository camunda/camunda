/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Suspense, useMemo, type ReactElement} from 'react';
import {useInfiniteQuery} from '@tanstack/react-query';
import {InlineLoading} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import type {ProcessDefinitionInstanceStatistics} from '@camunda/camunda-api-zod-schemas/8.10';
import {ErrorBoundary} from 'react-error-boundary';
import {tracking} from '#/shared/tracking';
import {InstancesBar} from '#/operate/components/InstancesBar/InstancesBar';
import {ExpandableList} from '../ExpandableList';
import {ExpandedRowErrorFallback} from '../ExpandedRowErrorFallback';
import {useDashboardScrollPagination} from '../useDashboardScrollPagination';
import {LinkWrapper, LoadingRow} from '../styled';
import {instancesByProcessInfiniteQuery, PAGE_SIZE} from './instancesByProcess.queries';
import {InstancesByProcessVersions} from './InstancesByProcessVersions';

const InstancesByProcess: React.FC = () => {
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
	} = useInfiniteQuery({...instancesByProcessInfiniteQuery(), refetchInterval: 5000});

	const items = useMemo(() => data?.pages.flatMap((page) => page.items) ?? [], [data]);

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
			items.map((item: ProcessDefinitionInstanceStatistics) => {
				const total = item.activeInstancesWithoutIncidentCount + item.activeInstancesWithIncidentCount;
				const versionKey = item.hasMultipleVersions
					? 'operate.dashboard.instancesInMultipleVersions'
					: 'operate.dashboard.instancesInOneVersion';
				const labelText = `${item.latestProcessDefinitionName || item.processDefinitionId} – ${t(versionKey, {count: total})}`;

				return {
					id: `${item.processDefinitionId}:${item.tenantId}`,
					instance: (
						<LinkWrapper
							to="/"
							title={labelText}
							onClick={() => {
								tracking.track({
									eventName: 'operate:navigation',
									link: 'dashboard-process-instances-by-name-all-versions',
								});
							}}
						>
							<InstancesBar
								label={{type: 'process', size: 'medium', text: labelText}}
								activeInstancesCount={item.activeInstancesWithoutIncidentCount}
								incidentsCount={item.activeInstancesWithIncidentCount}
								size="medium"
							/>
						</LinkWrapper>
					),
				};
			}),
		[items, t],
	);

	const expandedContents = useMemo(
		() =>
			items.reduce<Record<string, ReactElement<{tabIndex: number}>>>((accumulator, item) => {
				if (item.hasMultipleVersions) {
					accumulator[`${item.processDefinitionId}:${item.tenantId}`] = (
						<ErrorBoundary
							fallback={<ExpandedRowErrorFallback message={t('operate.dashboard.versionDetailsFetchError')} />}
						>
							<Suspense
								fallback={
									<LoadingRow>
										<InlineLoading />
									</LoadingRow>
								}
							>
								<InstancesByProcessVersions processDefinitionId={item.processDefinitionId} tenantId={item.tenantId} />
							</Suspense>
						</ErrorBoundary>
					);
				}
				return accumulator;
			}, {}),
		[items, t],
	);

	return (
		<ExpandableList
			isPending={isPending}
			isError={isError}
			listTestId="instances-by-process-list"
			dataTestId="instances-by-process-definition"
			header="instance"
			rows={rows}
			expandedContents={expandedContents}
			isFetchingNextPage={isFetchingNextPage}
			isFetchingPreviousPage={isFetchingPreviousPage}
			onScroll={onScroll}
		/>
	);
};

export {InstancesByProcess};
