/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Card, CardContent, DataTable} from '@camunda/design-system';
import {cn} from '#/shared/cn';
import {useRunningInstancesCount} from '../useRunningInstancesCount';

// Layout-only shell: content tiles are placeholders here and get wired in as their own
// PRs land (MetricPanel, InstancesByProcess, IncidentsByError, the empty states). Mirrors
// the Carbon Dashboard's grid: the metric panel spans the full width on top, the two lists
// sit side by side below it (or the single list fills the width when there are no
// instances). The two lists use DS DataTable directly (its own `title` prop replaces the
// Card/CardHeader wrapper) rather than a Card, since both will end up rendering columns
// and rows against it once InstancesByProcess/IncidentsByError land — see
// docs/migration/operate-dashboard-tiering.md for the component mapping.
const Dashboard: React.FC = () => {
	const {t} = useTranslation();
	const {data: count} = useRunningInstancesCount();
	const hasNoInstances = count.total === 0;

	return (
		<div className="flex h-full flex-col gap-4 overflow-hidden p-4">
			<h1 className="sr-only">{t('operate.dashboard.title')}</h1>
			<Card data-testid="metric-panel">
				<CardContent>{/* MetricPanel — wired in a later PR */}</CardContent>
			</Card>
			<div className={cn('grid flex-1 gap-4 overflow-hidden', !hasNoInstances && 'grid-cols-2')}>
				{/* NoInstancesEmptyState or the real columns/data for InstancesByProcess — wired in a later PR */}
				<DataTable
					title={t('operate.dashboard.processesByNameTitle')}
					columns={[]}
					data={[]}
					loading
					className="flex flex-col overflow-hidden"
				/>
				{!hasNoInstances && (
					// Real columns/data for IncidentsByError — wired in a later PR
					<DataTable
						title={t('operate.dashboard.incidentsByErrorTitle')}
						columns={[]}
						data={[]}
						loading
						className="flex flex-col overflow-hidden"
					/>
				)}
			</div>
		</div>
	);
};

export {Dashboard};
