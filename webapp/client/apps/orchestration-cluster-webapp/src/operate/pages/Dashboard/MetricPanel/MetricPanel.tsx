/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {SkeletonText} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import {InstancesBar} from '#/operate/components/InstancesBar/InstancesBar';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/ErrorMessage';
import {runningInstancesCountQuery} from '../useRunningInstancesCount';
import {runningOrAllInstancesFilter} from '../processesLinkFilters';
import {Title, LabelContainer, Label} from './styled';

const MetricPanel: React.FC = () => {
	const {t} = useTranslation();
	const {data: count, isPending, isError} = useQuery({...runningInstancesCountQuery(), refetchInterval: 5000});

	if (isError) {
		return <ErrorMessage message={t('operate.dashboard.metricFetchError')} />;
	}

	return (
		<>
			<Title
				data-testid="total-instances-link"
				to="/operate/processes"
				search={runningOrAllInstancesFilter(count?.total ?? 0)}
			>
				{count === undefined
					? t('operate.dashboard.runningInstancesTotalPending')
					: t('operate.dashboard.runningInstancesTotal', {count: count.total})}
			</Title>
			{count !== undefined && (
				<InstancesBar incidentsCount={count.withIncidents} activeInstancesCount={count.withoutIncidents} size="large" />
			)}
			{isPending && <SkeletonText data-testid="instances-bar-skeleton" />}
			<LabelContainer>
				<Label
					data-testid="incident-instances-link"
					to="/operate/processes"
					search={{active: false, incidents: true, completed: false, canceled: false, suspended: false}}
				>
					{t('operate.dashboard.instancesWithIncident')}
				</Label>
				<Label
					data-testid="active-instances-link"
					to="/operate/processes"
					search={{active: true, incidents: false, completed: false, canceled: false, suspended: false}}
				>
					{t('operate.dashboard.activeInstances')}
				</Label>
			</LabelContainer>
		</>
	);
};

export {MetricPanel};
