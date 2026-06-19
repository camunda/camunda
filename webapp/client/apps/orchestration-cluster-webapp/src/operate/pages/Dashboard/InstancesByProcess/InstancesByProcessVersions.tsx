/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import type {ProcessDefinitionInstanceVersionStatistics} from '@camunda/camunda-api-zod-schemas/8.10';
import {tracking} from '#/shared/tracking';
import {InstancesBar} from '#/operate/components/InstancesBar/InstancesBar';
import {useInstancesByProcessVersions} from './useInstancesByProcess';
import {Li, LinkWrapper} from './styled';

type Props = {
	processDefinitionId: string;
	tenantId: string | null;
	tabIndex?: number;
};

function InstancesByProcessVersions({processDefinitionId, tenantId, tabIndex}: Props) {
	const {t} = useTranslation();
	const {data} = useInstancesByProcessVersions(processDefinitionId, tenantId);

	return (
		<ul>
			{data.items.map((version: ProcessDefinitionInstanceVersionStatistics) => {
				const name = version.processDefinitionName ?? version.processDefinitionId;
				const total = version.activeInstancesWithoutIncidentCount + version.activeInstancesWithIncidentCount;
				const labelText = `${name} – ${t('operate.dashboard.instancesInVersion', {count: total, version: version.processDefinitionVersion})}`;

				return (
					<Li key={`${version.processDefinitionKey}:${version.tenantId}`}>
						<LinkWrapper
							to="/"
							tabIndex={tabIndex ?? 0}
							title={labelText}
							onClick={() => {
								tracking.track({
									eventName: 'operate:navigation',
									link: 'dashboard-process-instances-by-name-single-version',
								});
							}}
						>
							<InstancesBar
								label={{type: 'process', size: 'small', text: labelText}}
								activeInstancesCount={version.activeInstancesWithoutIncidentCount}
								incidentsCount={version.activeInstancesWithIncidentCount}
								size="small"
							/>
						</LinkWrapper>
					</Li>
				);
			})}
		</ul>
	);
}

export {InstancesByProcessVersions};
