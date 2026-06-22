/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Select, SelectItem} from '@carbon/react';
import {useSuspenseQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {queries} from '#/shared/http/queries';

const DEFAULT_TENANT_ID = '<default>';

type Props = {
	tenantId?: string;
} & Omit<React.ComponentProps<typeof Select>, 'disabled'>;

const ProcessesSelect: React.FC<Props> = ({tenantId = DEFAULT_TENANT_ID, ...props}) => {
	const {t} = useTranslation();

	const {data: processes} = useSuspenseQuery({
		...queries.queryProcessDefinitions({
			filter: {isLatestVersion: true, tenantId},
			page: {limit: 1000},
		}),
		select({items}) {
			return items.map(({processDefinitionKey, name, processDefinitionId, version}) => ({
				value: processDefinitionKey,
				label: `${name ?? processDefinitionId} - v${version}`,
			}));
		},
	});

	return (
		<Select {...props}>
			<SelectItem value="all" text={t('tasklist.customFiltersModalAllProcesses')} />
			{processes.map(({value, label}) => (
				<SelectItem key={value} value={value} text={label} />
			))}
		</Select>
	);
};

export {ProcessesSelect};
