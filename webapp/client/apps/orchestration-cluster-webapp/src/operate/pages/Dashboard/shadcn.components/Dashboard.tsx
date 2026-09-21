/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Card, CardContent, CardHeader, CardTitle} from '@camunda/design-system';
import {cn} from '#/shared/cn';
import {useRunningInstancesCount} from '../useRunningInstancesCount';

// Layout-only shell: content tiles are placeholders here and get wired in as their own
// PRs land (MetricPanel, InstancesByProcess, IncidentsByError, the empty states). Mirrors
// the Carbon Dashboard's grid: the metric panel spans the full width on top, the two lists
// sit side by side below it (or the single list fills the width when there are no
// instances). See docs/migration/operate-dashboard-tiering.md for the component mapping.
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
				<Card className="flex flex-col overflow-hidden">
					<CardHeader>
						<CardTitle>{t('operate.dashboard.processesByNameTitle')}</CardTitle>
					</CardHeader>
					<CardContent className="flex-1 overflow-auto">
						{/* NoInstancesEmptyState or InstancesByProcess — wired in a later PR */}
					</CardContent>
				</Card>
				{!hasNoInstances && (
					<Card className="flex flex-col overflow-hidden">
						<CardHeader>
							<CardTitle>{t('operate.dashboard.incidentsByErrorTitle')}</CardTitle>
						</CardHeader>
						<CardContent className="flex-1 overflow-auto">{/* IncidentsByError — wired in a later PR */}</CardContent>
					</Card>
				)}
			</div>
		</div>
	);
};

export {Dashboard};
