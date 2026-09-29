/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {DataTable, type DataTableColumn} from '@camunda/design-system';
import {cn} from '#/shared/cn';
import {useRunningInstancesCount} from '../useRunningInstancesCount';
import {NoInstancesEmptyState} from './NoInstancesEmptyState';
import {MetricPanel} from '../MetricPanel/shadcn.components/MetricPanel';
import {InstancesByProcess} from '../InstancesByProcess/shadcn.components/InstancesByProcess';

type SnapshotOnlyRow = {id: string; name: string};

// IncidentsByError lands in a later PR. These mock rows stand in until then, so the tile
// shows realistic populated content rather than an indefinite loading skeleton. The column
// header carries the tile's own title — an empty header fails the "table headers have
// discernible text" accessibility check.
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
		<main id="main-content" tabIndex={-1} className="flex h-full flex-col gap-4 overflow-hidden p-4">
			<h1 className="sr-only">{t('operate.dashboard.title')}</h1>
			<div data-testid="metric-panel">
				<MetricPanel count={count} />
			</div>
			<div className="h-4 shrink-0" aria-hidden="true" />
			<div className={cn('grid flex-1 gap-4 overflow-hidden', !hasNoInstances && 'grid-cols-2')}>
				{hasNoInstances ? (
					// InstancesByProcess fetches nothing when there are no instances, so the empty
					// state replaces it outright rather than rendering an empty list.
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
					// Real columns/data for IncidentsByError — wired in a later PR
					<DataTable
						aria-label={t('operate.dashboard.incidentsByErrorTitle')}
						columns={snapshotOnlyColumns(t('operate.dashboard.incidentsByErrorTitle'))}
						data={SNAPSHOT_ONLY_INCIDENT_ROWS}
						getRowId={(row) => row.id}
						className="flex flex-col overflow-hidden"
					/>
				)}
			</div>
		</main>
	);
};

export {Dashboard};
