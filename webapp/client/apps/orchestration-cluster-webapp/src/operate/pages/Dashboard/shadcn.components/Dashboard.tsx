/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {DataTable, Heading, type DataTableColumn} from '@camunda/design-system';
import {cn} from '#/shared/cn';
import {useRunningInstancesCount} from '../useRunningInstancesCount';
import {NoInstancesEmptyState} from './NoInstancesEmptyState';

type PlaceholderRow = {id: string; name: string};

// Real columns/data for InstancesByProcess and IncidentsByError land in later PRs. These mock
// rows stand in until then, so the tiles show realistic populated content rather than an
// indefinite loading skeleton. The column header carries the tile's own title — an empty
// header fails the "table headers have discernible text" accessibility check.
const placeholderColumns = (header: string): DataTableColumn<PlaceholderRow>[] => [
	{id: 'name', header, cell: ({row}) => row.original.name},
];
const SAMPLE_PROCESS_ROWS: PlaceholderRow[] = [
	{id: 'sample-process-1', name: 'Order process'},
	{id: 'sample-process-2', name: 'Shipping process'},
	{id: 'sample-process-3', name: 'Invoice process'},
];
const SAMPLE_INCIDENT_ROWS: PlaceholderRow[] = [
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
				<Heading as="h2" variant="heading-lg">
					{/* total-instances link — wired in a later PR */}
				</Heading>
				<div>{/* InstancesBar — wired in a later PR */}</div>
				<div>{/* incident/active instance labels — wired in a later PR */}</div>
			</div>
			<div className={cn('grid flex-1 gap-4 overflow-hidden', !hasNoInstances && 'grid-cols-2')}>
				{/* Real columns/data for InstancesByProcess — wired in a later PR */}
				<DataTable
					aria-label={t('operate.dashboard.processesByNameTitle')}
					columns={placeholderColumns(t('operate.dashboard.processesByNameTitle'))}
					data={hasNoInstances ? [] : SAMPLE_PROCESS_ROWS}
					getRowId={(row) => row.id}
					emptyState={hasNoInstances ? <NoInstancesEmptyState /> : undefined}
					className="flex flex-col overflow-hidden"
				/>
				{!hasNoInstances && (
					// Real columns/data for IncidentsByError — wired in a later PR
					<DataTable
						aria-label={t('operate.dashboard.incidentsByErrorTitle')}
						columns={placeholderColumns(t('operate.dashboard.incidentsByErrorTitle'))}
						data={SAMPLE_INCIDENT_ROWS}
						getRowId={(row) => row.id}
						className="flex flex-col overflow-hidden"
					/>
				)}
			</div>
		</main>
	);
};

export {Dashboard};
