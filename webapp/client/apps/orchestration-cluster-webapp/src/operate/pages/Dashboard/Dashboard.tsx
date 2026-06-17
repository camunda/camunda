/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Suspense} from 'react';
import {useTranslation} from 'react-i18next';
import {useRunningInstancesCount} from './useRunningInstancesCount';
import {Container, Grid, ScrollableContent, Tile, TileTitle, VisuallyHiddenH1} from './styled';
import {MetricPanel} from './MetricPanel/MetricPanel';
import {NoInstancesEmptyState} from './NoInstancesEmptyState';
import {InstancesByProcess} from './InstancesByProcess/InstancesByProcess';
import {IncidentsByError} from './IncidentsByError/IncidentsByError';

const Dashboard: React.FC = () => {
	const {t} = useTranslation();
	const {data: count} = useRunningInstancesCount();
	const hasNoInstances = count.total === 0;

	return (
		<Container>
			<Grid $numberOfColumns={hasNoInstances ? 1 : 2}>
				<VisuallyHiddenH1>{t('operate.dashboard.title')}</VisuallyHiddenH1>
				<Tile data-testid="metric-panel">
					<MetricPanel count={count} />
				</Tile>
				<Tile>
					<TileTitle>{t('operate.dashboard.processesByNameTitle')}</TileTitle>
					{hasNoInstances ? (
						<ScrollableContent>
							<NoInstancesEmptyState />
						</ScrollableContent>
					) : (
						<Suspense>
							<InstancesByProcess />
						</Suspense>
					)}
				</Tile>
				{!hasNoInstances && (
					<Tile>
						<TileTitle>{t('operate.dashboard.incidentsByErrorTitle')}</TileTitle>
						<Suspense>
							<IncidentsByError />
						</Suspense>
					</Tile>
				)}
			</Grid>
		</Container>
	);
};

export {Dashboard};
