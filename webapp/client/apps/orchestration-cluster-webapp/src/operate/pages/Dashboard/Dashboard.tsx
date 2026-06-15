/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSuspenseQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {queries} from '#/shared/http/queries';
import {Grid, ScrollableContent, Tile, TileTitle, VisuallyHiddenH1} from './styled';
import {MetricPanel} from './MetricPanel/MetricPanel';
import {NoInstancesEmptyState} from './NoInstancesEmptyState';

const Dashboard: React.FC = () => {
	const {t} = useTranslation();
	const {data: count} = useSuspenseQuery(queries.getRunningInstancesCount());
	const hasNoInstances = count.total === 0;

	return (
		<Grid $numberOfColumns={hasNoInstances ? 1 : 2}>
			<VisuallyHiddenH1>{t('operate.dashboard.title')}</VisuallyHiddenH1>
			<Tile data-testid="metric-panel">
				<MetricPanel count={count} />
			</Tile>
			<Tile>
				<TileTitle>{t('operate.dashboard.processesByNameTitle')}</TileTitle>
				<ScrollableContent>{hasNoInstances ? <NoInstancesEmptyState /> : null}</ScrollableContent>
			</Tile>
			{!hasNoInstances && (
				<Tile>
					<TileTitle>{t('operate.dashboard.incidentsByErrorTitle')}</TileTitle>
					<ScrollableContent />
				</Tile>
			)}
		</Grid>
	);
};

export {Dashboard};
