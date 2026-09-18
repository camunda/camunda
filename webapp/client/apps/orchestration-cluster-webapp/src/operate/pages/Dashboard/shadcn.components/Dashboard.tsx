/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {DataTable, Heading} from '@camunda/design-system';
import {cn} from '#/shared/cn';
import {useRunningInstancesCount} from '../useRunningInstancesCount';

// Layout-only shell: content tiles are placeholders here and get wired in as their own
// PRs land (MetricPanel, InstancesByProcess, IncidentsByError, the empty states). Mirrors
// the Carbon Dashboard's grid: the metric panel spans the full width on top, the two lists
// sit side by side below it (or the single list fills the width when there are no
// instances). Neither the metric panel nor the two lists use a Card: the metric panel's
// eventual title is a DS `Heading` (top-level heading style, matching Carbon's
// productiveHeading04) with its other children left as generic divs for MetricPanel to
// style once it lands; the two lists use DS DataTable directly (its own `title` prop
// replaces the Card/CardHeader wrapper), since both will end up rendering columns and rows
// against it once InstancesByProcess/IncidentsByError land — see
// docs/migration/operate-dashboard-tiering.md for the component mapping.
const Dashboard: React.FC = () => {
	const {t} = useTranslation();
	const {data: count} = useRunningInstancesCount();
	const hasNoInstances = count.total === 0;

	return (
		<div className="flex h-full flex-col gap-4 overflow-hidden p-4">
			<h1 className="sr-only">{t('operate.dashboard.title')}</h1>
			<div data-testid="metric-panel">
				<Heading as="h2" variant="heading-lg">
					{/* total-instances link — wired in a later PR */}
				</Heading>
				<div>{/* InstancesBar — wired in a later PR */}</div>
				<div>{/* incident/active instance labels — wired in a later PR */}</div>
			</div>
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
