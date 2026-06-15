/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {tracking} from '#/shared/tracking';
import {InstancesBar} from '#/operate/components/InstancesBar/InstancesBar';
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
				to="/"
				onClick={() => {
					tracking.track({
						eventName: 'operate:navigation',
						link: 'dashboard-running-processes',
					});
				}}
			>
				{t('operate.dashboard.runningInstancesTotal', {count: count.total})}
			</Title>
			<InstancesBar incidentsCount={count.withIncidents} activeInstancesCount={count.withoutIncidents} size="large" />
			<LabelContainer>
				<Label
					data-testid="incident-instances-link"
					to="/"
					onClick={() => {
						tracking.track({
							eventName: 'operate:navigation',
							link: 'dashboard-processes-with-incidents',
						});
					}}
				>
					{t('operate.dashboard.instancesWithIncident')}
				</Label>
				<Label
					data-testid="active-instances-link"
					to="/"
					onClick={() => {
						tracking.track({
							eventName: 'operate:navigation',
							link: 'dashboard-active-processes',
						});
					}}
				>
					{t('operate.dashboard.activeInstances')}
				</Label>
			</LabelContainer>
		</>
	);
};

export {MetricPanel};
