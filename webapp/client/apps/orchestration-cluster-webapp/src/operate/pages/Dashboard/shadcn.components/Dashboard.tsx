/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {DataTable, Heading, PageLayout, Separator, type DataTableColumn} from '@camunda/design-system';
import {cn} from '#/shared/cn';
import {useRunningInstancesCount} from '../useRunningInstancesCount';
import {NoInstancesEmptyState} from './NoInstancesEmptyState';
import {MetricPanel} from '../MetricPanel/shadcn.components/MetricPanel';
import {InstancesByProcess} from '../InstancesByProcess/shadcn.components/InstancesByProcess';
import {IncidentsByError} from '../IncidentsByError/shadcn.components/IncidentsByError';

type NoInstancesTableSkeletonRow = {id: string; name: string};

const noInstancesTableSkeletonColumns = (header: string): DataTableColumn<NoInstancesTableSkeletonRow>[] => [
	{id: 'name', header, cell: ({row}) => row.original.name},
];

const Dashboard: React.FC = () => {
	const {t} = useTranslation();
	const {data: count} = useRunningInstancesCount();
	const hasNoInstances = count.total === 0;

	return (
		<PageLayout id="main-content" tabIndex={-1} padding="none" width="full" className="h-full">
			<div className="flex h-full min-h-0 flex-1 flex-col gap-4 overflow-hidden px-6 py-4">
				<Heading as="h1" className="sr-only">
					{t('operate.dashboard.title')}
				</Heading>
				<div data-testid="metric-panel">
					<MetricPanel count={count} />
				</div>
				<Separator className="my-2" />
				<div className={cn('grid flex-1 gap-4 overflow-hidden', !hasNoInstances && 'grid-cols-2')}>
					{hasNoInstances ? (
						<DataTable
							aria-label={t('operate.dashboard.processesByNameTitle')}
							columns={noInstancesTableSkeletonColumns(t('operate.dashboard.processesByNameTitle'))}
							data={[]}
							getRowId={(row) => row.id}
							emptyState={<NoInstancesEmptyState />}
							className="flex flex-col overflow-hidden"
						/>
					) : (
						<InstancesByProcess />
					)}
					{!hasNoInstances && <IncidentsByError />}
				</div>
			</div>
		</PageLayout>
	);
};

export {Dashboard};
