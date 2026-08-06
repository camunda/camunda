/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {InstancesBar} from '#/operate/components/InstancesBar/InstancesBar';
import {runningOrAllInstancesFilter} from '../processesLinkFilters';
import {Title, LabelContainer, Label} from './styled';

type RunningInstancesCount = {
	total: number;
	withIncidents: number;
	withoutIncidents: number;
};

type Props = {
	count: RunningInstancesCount;
};

const MetricPanel: React.FC<Props> = ({count}) => {
	const {t} = useTranslation();

	return (
		<>
			<Title
				data-testid="total-instances-link"
				to="/operate/processes"
				search={runningOrAllInstancesFilter(count.total)}
			>
				{t('operate.dashboard.runningInstancesTotal', {count: count.total})}
			</Title>
			<InstancesBar incidentsCount={count.withIncidents} activeInstancesCount={count.withoutIncidents} size="large" />
			<LabelContainer>
				<Label
					data-testid="incident-instances-link"
					to="/operate/processes"
					search={{active: false, incidents: true, completed: false, canceled: false}}
				>
					{t('operate.dashboard.instancesWithIncident')}
				</Label>
				<Label
					data-testid="active-instances-link"
					to="/operate/processes"
					search={{active: true, incidents: false, completed: false, canceled: false}}
				>
					{t('operate.dashboard.activeInstances')}
				</Label>
			</LabelContainer>
		</>
	);
};

export {MetricPanel};
