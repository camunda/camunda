/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Link} from '@tanstack/react-router';
import {InstancesBar} from '#/operate/components/InstancesBar/shadcn.components/InstancesBar';
import {runningOrAllInstancesFilter} from '../../processesLinkFilters';

type RunningInstancesCount = {
	total: number;
	withIncidents: number;
	withoutIncidents: number;
};

type Props = {
	count: RunningInstancesCount;
};

// Carbon's productiveHeading04/03 are 28px/20px at 600 weight; DS's heading-lg/heading-md
// are the same sizes but 400 weight — font-semibold added to keep Carbon's visual weight.
const MetricPanel: React.FC<Props> = ({count}) => {
	const {t} = useTranslation();

	return (
		<>
			<Link
				data-testid="total-instances-link"
				to="/operate/processes"
				search={runningOrAllInstancesFilter(count.total)}
				className="mb-4 inline-block text-[28px] leading-9 font-semibold text-foreground hover:underline"
			>
				{t('operate.dashboard.runningInstancesTotal', {count: count.total})}
			</Link>
			<InstancesBar incidentsCount={count.withIncidents} activeInstancesCount={count.withoutIncidents} size="large" />
			<div className="flex w-full justify-between">
				<Link
					data-testid="incident-instances-link"
					to="/operate/processes"
					search={{active: false, incidents: true, completed: false, canceled: false, suspended: false}}
					className="text-xl leading-7 font-semibold text-foreground hover:underline"
				>
					{t('operate.dashboard.instancesWithIncident')}
				</Link>
				<Link
					data-testid="active-instances-link"
					to="/operate/processes"
					search={{active: true, incidents: false, completed: false, canceled: false, suspended: false}}
					className="text-xl leading-7 font-semibold text-foreground hover:underline"
				>
					{t('operate.dashboard.activeInstances')}
				</Link>
			</div>
		</>
	);
};

export {MetricPanel};
