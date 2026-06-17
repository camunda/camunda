/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useCallback, useMemo} from 'react';
import {InlineLoading} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import type {ProcessDefinitionInstanceStatistics} from '@camunda/camunda-api-zod-schemas/8.10';
import {tracking} from '#/shared/tracking';
import {InstancesBar} from '#/operate/components/InstancesBar/InstancesBar';
import {PartiallyExpandableDataTable} from '../PartiallyExpandableDataTable/PartiallyExpandableDataTable';
import {useInstancesByProcess, PAGE_SIZE} from './useInstancesByProcess';
import {InstancesByProcessVersions} from './InstancesByProcessVersions';
import {ScrollableList, LoadingRow, LinkWrapper} from './styled';

const ROW_HEIGHT = 64;

function InstancesByProcess() {
	const {t} = useTranslation();
	const {
		data,
		fetchNextPage,
		fetchPreviousPage,
		hasNextPage,
		hasPreviousPage,
		isFetchingNextPage,
		isFetchingPreviousPage,
	} = useInstancesByProcess();

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
			items.reduce<Record<string, React.ReactElement<{tabIndex: number}>>>((accumulator, item) => {
				if (item.hasMultipleVersions) {
					accumulator[`${item.processDefinitionId}:${item.tenantId}`] = (
						<InstancesByProcessVersions processDefinitionId={item.processDefinitionId} tenantId={item.tenantId} />
					);
				}
				return accumulator;
			}, {}),
		[items],
	);

	return (
		<ScrollableList onScroll={handleScroll} data-testid="instances-by-process-list">
			{isFetchingPreviousPage && (
				<LoadingRow>
					<InlineLoading />
				</LoadingRow>
			)}
			<PartiallyExpandableDataTable
				dataTestId="instances-by-process-definition"
				headers={[{key: 'instance', header: 'instance'}]}
				rows={rows}
				expandedContents={expandedContents}
			/>
			{isFetchingNextPage && (
				<LoadingRow>
					<InlineLoading />
				</LoadingRow>
			)}
		</ScrollableList>
	);
}

export {InstancesByProcess};
