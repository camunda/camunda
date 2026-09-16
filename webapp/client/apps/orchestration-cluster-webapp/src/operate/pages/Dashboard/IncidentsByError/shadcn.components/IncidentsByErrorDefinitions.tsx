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
import type {IncidentProcessInstanceStatisticsByDefinition} from '@camunda/camunda-api-zod-schemas/8.10';
import {InstancesBar} from '#/operate/components/InstancesBar/shadcn.components/InstancesBar';
import {incidentsByErrorDefinitionsQuery} from '../incidentsByError.queries';

type Props = {
	errorHashCode: number;
	errorMessage: string;
	tabIndex?: number;
};

const IncidentsByErrorDefinitions: React.FC<Props> = ({errorHashCode, errorMessage, tabIndex}) => {
	const {t} = useTranslation();
	const {data} = useSuspenseQuery(incidentsByErrorDefinitionsQuery(errorHashCode));

	return (
		<ul>
			{data.items.map((item: IncidentProcessInstanceStatisticsByDefinition) => {
				const labelText = `${item.processDefinitionName ?? item.processDefinitionId} – ${t('operate.dashboard.version', {version: item.processDefinitionVersion})}`;

				return (
					<li key={`${item.processDefinitionKey}:${item.tenantId}`} className="hover:bg-neutral-background-medium">
						<Link
							to="/operate/processes"
							search={{
								process: item.processDefinitionId,
								version: item.processDefinitionVersion,
								errorMessage,
								incidents: true,
								active: false,
								completed: false,
								canceled: false,
								suspended: false,
							}}
							tabIndex={tabIndex ?? 0}
							title={labelText}
							className="block py-1 no-underline"
						>
							<InstancesBar
								label={{type: 'incident', size: 'small', text: labelText}}
								incidentsCount={item.activeInstancesWithErrorCount}
								size="small"
							/>
						</Link>
					</li>
				);
			})}
		</ul>
	);
};

export {IncidentsByErrorDefinitions};
