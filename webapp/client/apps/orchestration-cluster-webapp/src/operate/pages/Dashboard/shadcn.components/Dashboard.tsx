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

// A DataTable with zero columns renders zero-cell skeleton rows, which collapse to no
// height at all — no loading indication shows up. One columnless placeholder column
// gives the loading skeleton a cell to paint into; real columns replace this once
// InstancesByProcess/IncidentsByError land.
const PLACEHOLDER_COLUMNS: DataTableColumn<never>[] = [{id: 'placeholder', header: ''}];

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
					title={t('operate.dashboard.processesByNameTitle')}
					columns={PLACEHOLDER_COLUMNS}
					data={[]}
					loading={!hasNoInstances}
					emptyState={hasNoInstances ? <NoInstancesEmptyState /> : undefined}
					className="flex flex-col overflow-hidden"
				/>
				{!hasNoInstances && (
					// Real columns/data for IncidentsByError — wired in a later PR
					<DataTable
						title={t('operate.dashboard.incidentsByErrorTitle')}
						columns={PLACEHOLDER_COLUMNS}
						data={[]}
						loading
						className="flex flex-col overflow-hidden"
					/>
				)}
			</div>
		</main>
	);
};

export {Dashboard};
