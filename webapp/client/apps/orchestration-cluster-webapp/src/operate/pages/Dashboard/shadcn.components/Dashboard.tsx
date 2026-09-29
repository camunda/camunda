/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {DataTable, PageLayout, Separator, type DataTableColumn} from '@camunda/design-system';
import {cn} from '#/shared/cn';
import {useRunningInstancesCount} from '../useRunningInstancesCount';
import {NoInstancesEmptyState} from './NoInstancesEmptyState';
import {MetricPanel} from '../MetricPanel/shadcn.components/MetricPanel';
import {InstancesByProcess} from '../InstancesByProcess/shadcn.components/InstancesByProcess';

type SnapshotOnlyRow = {id: string; name: string};

const snapshotOnlyColumns = (header: string): DataTableColumn<SnapshotOnlyRow>[] => [
	{id: 'name', header, cell: ({row}) => row.original.name},
];
const SNAPSHOT_ONLY_INCIDENT_ROWS: SnapshotOnlyRow[] = [
	{id: 'sample-incident-1', name: 'Connection timeout'},
	{id: 'sample-incident-2', name: 'Null pointer exception'},
];

const Dashboard: React.FC = () => {
	const {t} = useTranslation();
	const {data: count} = useRunningInstancesCount();
	const hasNoInstances = count.total === 0;

	return (
		<PageLayout id="main-content" tabIndex={-1} padding="none" width="full" className="h-full">
			<div className="flex h-full min-h-0 flex-1 flex-col gap-4 overflow-hidden px-6 py-4">
				<h1 className="sr-only">{t('operate.dashboard.title')}</h1>
				<div data-testid="metric-panel">
					<MetricPanel count={count} />
				</div>
				<Separator className="my-2" />
				<div className={cn('grid flex-1 gap-4 overflow-hidden', !hasNoInstances && 'grid-cols-2')}>
					{hasNoInstances ? (
						<DataTable
							aria-label={t('operate.dashboard.processesByNameTitle')}
							columns={snapshotOnlyColumns(t('operate.dashboard.processesByNameTitle'))}
							data={[]}
							getRowId={(row) => row.id}
							emptyState={<NoInstancesEmptyState />}
							className="flex flex-col overflow-hidden"
						/>
					) : (
						<InstancesByProcess />
					)}
					{!hasNoInstances && (
						<DataTable
							aria-label={t('operate.dashboard.incidentsByErrorTitle')}
							columns={snapshotOnlyColumns(t('operate.dashboard.incidentsByErrorTitle'))}
							data={SNAPSHOT_ONLY_INCIDENT_ROWS}
							getRowId={(row) => row.id}
							className="flex flex-col overflow-hidden"
						/>
					)}
				</div>
			</div>
		</PageLayout>
	);
};

export {Dashboard};
