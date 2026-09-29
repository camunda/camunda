/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {InlineLoading, InlineNotification} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {isSpecificTenant} from '#/operate/shared/utils/isSpecificTenant';
import type {OperationsLogSearch} from './operationsLog.schema';
import {selectedDefinitionsQuery} from './definitions.queries';
import {Filters} from './Filters';
import {InstancesTable} from './InstancesTable';
import {PageContainer} from './styled';

const OperationsLog: React.FC<OperationsLogSearch> = (search) => {
	const {t} = useTranslation();
	const needsLookup = Boolean(
		search.process &&
		(search.version !== undefined ||
			(getClientConfig().deployment.isMultiTenancyEnabled && !isSpecificTenant(search.tenantId))),
	);
	const {
		data: definitions,
		isPending,
		isError,
	} = useQuery({
		...selectedDefinitionsQuery(search.process ?? '', search.tenantId),
		enabled: needsLookup,
		retry: false,
	});
	const matches = definitions?.filter(
		(definition) =>
			definition.processDefinitionId === search.process &&
			(!isSpecificTenant(search.tenantId) || definition.tenantId === search.tenantId),
	);
	const candidates =
		search.version === undefined ? matches : matches?.filter((definition) => definition.version === search.version);
	const tenants = new Set(candidates?.map((definition) => definition.tenantId));
	const tenantId = isSpecificTenant(search.tenantId)
		? search.tenantId
		: tenants.size === 1
			? [...tenants][0]
			: undefined;
	const selectedDefinition =
		search.version === undefined ? undefined : candidates?.find((definition) => definition.tenantId === tenantId);
	const isResolved =
		!needsLookup ||
		(tenantId !== undefined &&
			matches !== undefined &&
			matches.length > 0 &&
			(search.version === undefined || selectedDefinition !== undefined));

	return (
		<PageContainer>
			<Filters search={search} />
			{needsLookup && isPending ? (
				<InlineLoading description={t('operate.operationsLog.filters.resolvingDefinition')} />
			) : needsLookup && isError ? (
				<InlineNotification kind="error" title={t('operate.operationsLog.filters.definitionLookupFailed')} />
			) : !isResolved ? (
				<InlineNotification kind="warning" title={t('operate.operationsLog.filters.definitionUnavailable')} />
			) : (
				<InstancesTable
					search={search}
					selectedTenantId={search.process ? tenantId : isSpecificTenant(search.tenantId) ? search.tenantId : undefined}
					selectedDefinitionKey={selectedDefinition?.processDefinitionKey}
				/>
			)}
		</PageContainer>
	);
};

export {OperationsLog};
