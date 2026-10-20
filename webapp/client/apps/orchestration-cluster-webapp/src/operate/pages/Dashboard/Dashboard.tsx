/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useInfiniteQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Container, Grid, ScrollableContent, Tile, TileTitle, VisuallyHiddenH1} from './styled';
import {MetricPanel} from './MetricPanel/MetricPanel';
import {NoInstancesEmptyState} from './NoInstancesEmptyState';
import {InstancesByProcess} from './InstancesByProcess/InstancesByProcess';
import {instancesByProcessInfiniteQuery} from './InstancesByProcess/instancesByProcess.queries';
import {IncidentsByError} from './IncidentsByError/IncidentsByError';

const Dashboard: React.FC = () => {
	const {t} = useTranslation();
	const processStats = useInfiniteQuery({
		...instancesByProcessInfiniteQuery(),
		refetchInterval: 5000,
		select: (data) => data.pages[0]?.page.totalItems ?? 0,
	});
	const hasNoInstances = processStats.status === 'success' && processStats.data === 0;

	return (
		<Container>
			<Grid $numberOfColumns={hasNoInstances ? 1 : 2}>
				<VisuallyHiddenH1>{t('operate.dashboard.title')}</VisuallyHiddenH1>
				<Tile data-testid="metric-panel">
					<MetricPanel />
				</Tile>
				<Tile>
					<TileTitle>{t('operate.dashboard.processesByNameTitle')}</TileTitle>
					{hasNoInstances ? (
						<ScrollableContent>
							<NoInstancesEmptyState />
						</ScrollableContent>
					) : (
						<InstancesByProcess />
					)}
				</Tile>
				{!hasNoInstances && (
					<Tile>
						<TileTitle>{t('operate.dashboard.incidentsByErrorTitle')}</TileTitle>
						<IncidentsByError />
					</Tile>
				)}
			</Grid>
		</Container>
	);
};

export {Dashboard};
