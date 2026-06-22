/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import type {IncidentProcessInstanceStatisticsByDefinition} from '@camunda/camunda-api-zod-schemas/8.10';
import {tracking} from '#/shared/tracking';
import {InstancesBar} from '#/operate/components/InstancesBar/InstancesBar';
import {useIncidentsByErrorDefinitions} from './useIncidentsByError';
import {Li, LinkWrapper} from './styled';

type Props = {
	errorHashCode: number;
	tabIndex?: number;
};

const IncidentsByErrorDefinitions: React.FC<Props> = ({errorHashCode, tabIndex}) => {
	const {t} = useTranslation();
	const {data} = useIncidentsByErrorDefinitions(errorHashCode);

	return (
		<ul>
			{data.items.map((item: IncidentProcessInstanceStatisticsByDefinition) => {
				const labelText = `${item.processDefinitionName ?? item.processDefinitionId} – ${t('operate.dashboard.version', {version: item.processDefinitionVersion})}`;

				return (
					<Li key={`${item.processDefinitionKey}:${item.tenantId}`}>
						<LinkWrapper
							to="/"
							tabIndex={tabIndex ?? 0}
							title={labelText}
							onClick={() => {
								tracking.track({
									eventName: 'operate:navigation',
									link: 'dashboard-process-incidents-by-error-message-single-version',
								});
							}}
						>
							<InstancesBar
								label={{type: 'incident', size: 'small', text: labelText}}
								incidentsCount={item.activeInstancesWithErrorCount}
								size="small"
							/>
						</LinkWrapper>
					</Li>
				);
			})}
		</ul>
	);
};

export {IncidentsByErrorDefinitions};
