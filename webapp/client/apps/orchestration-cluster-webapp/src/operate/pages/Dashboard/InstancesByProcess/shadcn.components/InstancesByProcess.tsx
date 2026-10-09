/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Suspense, useMemo} from 'react';
import {useInfiniteQuery, useQuery} from '@tanstack/react-query';
import {Skeleton} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';
import {Link} from '@tanstack/react-router';
import type {ProcessDefinitionInstanceStatistics} from '@camunda/camunda-api-zod-schemas/8.11';
import {ErrorBoundary} from 'react-error-boundary';
import {InstancesBar} from '#/operate/components/InstancesBar/shadcn.components/InstancesBar';
import {ExpandableList} from '../../shadcn.components/ExpandableList';
import {ExpandedRowErrorFallback} from '../../shadcn.components/ExpandedRowErrorFallback';
import {dashboardTenantId, runningOrAllInstancesFilter, useDashboardTenants} from '../../processesLinkFilters';
import {
	drainingByIdKey,
	drainingProcessDefinitionsQuery,
	instancesByProcessInfiniteQuery,
} from '../instancesByProcess.queries';
import {InstancesByProcessVersions} from './InstancesByProcessVersions';

const InstancesByProcess: React.FC = () => {
	const {t} = useTranslation();
	const {isMultiTenancyEnabled, tenantsById} = useDashboardTenants();
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
		isFetchNextPageError,
		isFetchPreviousPageError,
	} = useInfiniteQuery({...instancesByProcessInfiniteQuery(), refetchInterval: 5000});

	const {data: draining} = useQuery(drainingProcessDefinitionsQuery());

	const items = useMemo(() => data?.pages.flatMap((page) => page.items) ?? [], [data]);
	const hasItems = items.length > 0;

	const rows = useMemo(
		() =>
			items.map((item: ProcessDefinitionInstanceStatistics) => {
				const total = item.activeInstancesWithoutIncidentCount + item.activeInstancesWithIncidentCount;
				const versionKey = item.hasMultipleVersions
					? 'operate.dashboard.instancesInMultipleVersions'
					: 'operate.dashboard.instancesInOneVersion';
				const tenantId = dashboardTenantId(item.tenantId, isMultiTenancyEnabled);
				const tenantName = tenantId ? (tenantsById[tenantId] ?? tenantId) : undefined;
				const name = item.latestProcessDefinitionName || item.processDefinitionId;
				const isDraining = !!draining?.byId.has(drainingByIdKey(item.tenantId, item.processDefinitionId));
				const drainingDescription = t('operate.dashboard.drainingDescriptionAllVersions');
				const labelText = tenantName
					? t(
							item.hasMultipleVersions
								? 'operate.dashboard.instancesInMultipleVersionsWithTenant'
								: 'operate.dashboard.instancesInOneVersionWithTenant',
							{name, count: total, tenant: tenantName},
						)
					: `${name} – ${t(versionKey, {count: total})}`;

				return {
					id: `${item.processDefinitionId}:${item.tenantId}`,
					content: (
						<Link
							to="/operate/processes"
							search={{process: item.processDefinitionId, tenantId, ...runningOrAllInstancesFilter(total)}}
							title={isDraining ? `${labelText} – ${drainingDescription}` : labelText}
							className="block py-1 no-underline"
						>
							<InstancesBar
								label={{type: 'process', size: 'medium', text: labelText}}
								activeInstancesCount={item.activeInstancesWithoutIncidentCount}
								incidentsCount={item.activeInstancesWithIncidentCount}
								isDraining={isDraining}
								drainingDescription={drainingDescription}
								isDrainingIndicatorFocusable={false}
								size="medium"
							/>
						</Link>
					),
				};
			}),
		[items, t, draining, isMultiTenancyEnabled, tenantsById],
	);

	const expandedContents = useMemo(
		() =>
			items.reduce<Record<string, React.ReactElement<{tabIndex: number}>>>((accumulator, item) => {
				if (item.hasMultipleVersions) {
					accumulator[`${item.processDefinitionId}:${item.tenantId}`] = (
						<ErrorBoundary
							fallback={<ExpandedRowErrorFallback message={t('operate.dashboard.versionDetailsFetchError')} />}
						>
							<Suspense fallback={<Skeleton className="my-1 h-6 w-full" />}>
								<InstancesByProcessVersions
									processDefinitionId={item.processDefinitionId}
									tenantId={item.tenantId}
									drainingDefinitionKeys={draining?.byKey}
								/>
							</Suspense>
						</ErrorBoundary>
					);
				}
				return accumulator;
			}, {}),
		[items, t, draining?.byKey],
	);

	return (
		<ExpandableList
			isPending={isPending}
			isError={isError && !hasItems}
			listTestId="instances-by-process-list"
			dataTestId="instances-by-process-definition"
			header={t('operate.dashboard.processesByNameTitle')}
			rows={rows}
			expandedContents={expandedContents}
			variant="nativeExpansion"
			hasNextPage={hasNextPage}
			hasPreviousPage={hasPreviousPage}
			isFetchingNextPage={isFetchingNextPage}
			isFetchingPreviousPage={isFetchingPreviousPage}
			isFetchNextPageError={isFetchNextPageError}
			isFetchPreviousPageError={isFetchPreviousPageError}
			onLoadNextPage={fetchNextPage}
			onLoadPreviousPage={fetchPreviousPage}
		/>
	);
};

export {InstancesByProcess};
