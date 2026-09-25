/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSuspenseQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import type {ProcessDefinitionInstanceVersionStatistics} from '@camunda/camunda-api-zod-schemas/8.10';
import {InstancesBar} from '#/operate/components/InstancesBar/InstancesBar';
import {instancesByProcessVersionsQuery, type DrainingLookup} from './instancesByProcess.queries';
import {dashboardTenantId, runningOrAllInstancesFilter} from '../processesLinkFilters';
import {Li, LinkWrapper} from '../styled';

type Props = {
	processDefinitionId: string;
	tenantId: string | null;
	drainingDefinitionKeys?: DrainingLookup['byKey'];
	isMultiTenancyEnabled: boolean;
	tenantsById: Record<string, string>;
	tabIndex?: number;
};

const InstancesByProcessVersions: React.FC<Props> = ({
	processDefinitionId,
	tenantId,
	drainingDefinitionKeys,
	isMultiTenancyEnabled,
	tenantsById,
	tabIndex,
}) => {
	const {t} = useTranslation();
	const {data} = useSuspenseQuery(instancesByProcessVersionsQuery(processDefinitionId, tenantId));

	return (
		<ul>
			{data.items.map((version: ProcessDefinitionInstanceVersionStatistics) => {
				const name = version.processDefinitionName ?? version.processDefinitionId;
				const total = version.activeInstancesWithoutIncidentCount + version.activeInstancesWithIncidentCount;
				const linkTenantId = dashboardTenantId(version.tenantId, isMultiTenancyEnabled);
				const tenantName = linkTenantId ? (tenantsById[linkTenantId] ?? linkTenantId) : undefined;
				const labelText = tenantName
					? t('operate.dashboard.instancesInVersionWithTenant', {
							name,
							count: total,
							version: version.processDefinitionVersion,
							tenant: tenantName,
						})
					: `${name} – ${t('operate.dashboard.instancesInVersion', {count: total, version: version.processDefinitionVersion})}`;

				return (
					<Li key={`${version.processDefinitionKey}:${version.tenantId}`}>
						<LinkWrapper
							to="/operate/processes"
							search={{
								process: version.processDefinitionId,
								version: version.processDefinitionVersion,
								tenantId: linkTenantId,
								...runningOrAllInstancesFilter(total),
							}}
							tabIndex={tabIndex ?? 0}
							title={labelText}
						>
							<InstancesBar
								label={{type: 'process', size: 'small', text: labelText}}
								activeInstancesCount={version.activeInstancesWithoutIncidentCount}
								incidentsCount={version.activeInstancesWithIncidentCount}
								isDraining={!!drainingDefinitionKeys?.has(version.processDefinitionKey)}
								drainingDescription={t('operate.dashboard.drainingDescriptionVersion')}
								size="small"
							/>
						</LinkWrapper>
					</Li>
				);
			})}
		</ul>
	);
};

export {InstancesByProcessVersions};
