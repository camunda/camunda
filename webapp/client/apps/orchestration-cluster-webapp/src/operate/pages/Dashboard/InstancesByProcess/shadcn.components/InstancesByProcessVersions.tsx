/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSuspenseQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Link} from '@tanstack/react-router';
import type {ProcessDefinitionInstanceVersionStatistics} from '@camunda/camunda-api-zod-schemas/8.11';
import {InstancesBar} from '#/operate/components/InstancesBar/shadcn.components/InstancesBar';
import {
	instancesByProcessVersionsQuery,
	type DrainingLookup,
} from '#/operate/pages/Dashboard/InstancesByProcess/instancesByProcess.queries';
import {
	dashboardTenantId,
	runningOrAllInstancesFilter,
	useDashboardTenants,
} from '#/operate/pages/Dashboard/processesLinkFilters';

type Props = {
	processDefinitionId: string;
	tenantId: string | null;
	drainingDefinitionKeys?: DrainingLookup['byKey'];
	tabIndex?: number;
};

const InstancesByProcessVersions: React.FC<Props> = ({
	processDefinitionId,
	tenantId,
	drainingDefinitionKeys,
	tabIndex,
}) => {
	const {t} = useTranslation();
	const {isMultiTenancyEnabled, tenantsById} = useDashboardTenants();
	const {data} = useSuspenseQuery(instancesByProcessVersionsQuery(processDefinitionId, tenantId));

	return (
		<ul>
			{data.items.map((version: ProcessDefinitionInstanceVersionStatistics) => {
				const name = version.processDefinitionName ?? version.processDefinitionId;
				const total = version.activeInstancesWithoutIncidentCount + version.activeInstancesWithIncidentCount;
				const linkTenantId = dashboardTenantId(version.tenantId, isMultiTenancyEnabled);
				const tenantName = linkTenantId ? (tenantsById[linkTenantId] ?? linkTenantId) : undefined;
				const isDraining = !!drainingDefinitionKeys?.has(version.processDefinitionKey);
				const drainingDescription = t('operate.dashboard.drainingDescriptionVersion');
				const labelText = tenantName
					? t('operate.dashboard.instancesInVersionWithTenant', {
							name,
							count: total,
							version: version.processDefinitionVersion,
							tenant: tenantName,
						})
					: `${name} – ${t('operate.dashboard.instancesInVersion', {count: total, version: version.processDefinitionVersion})}`;

				return (
					<li
						key={`${version.processDefinitionKey}:${version.tenantId}`}
						className="hover:bg-neutral-background-medium"
					>
						<Link
							to="/operate/processes"
							search={{
								process: version.processDefinitionId,
								version: version.processDefinitionVersion,
								tenantId: linkTenantId,
								...runningOrAllInstancesFilter(total),
							}}
							tabIndex={tabIndex ?? 0}
							title={isDraining ? `${labelText} – ${drainingDescription}` : labelText}
							className="block py-1 no-underline"
						>
							<InstancesBar
								label={{type: 'process', size: 'small', text: labelText}}
								activeInstancesCount={version.activeInstancesWithoutIncidentCount}
								incidentsCount={version.activeInstancesWithIncidentCount}
								isDraining={isDraining}
								drainingDescription={drainingDescription}
								isDrainingIndicatorFocusable={false}
								size="small"
							/>
						</Link>
					</li>
				);
			})}
		</ul>
	);
};

export {InstancesByProcessVersions};
