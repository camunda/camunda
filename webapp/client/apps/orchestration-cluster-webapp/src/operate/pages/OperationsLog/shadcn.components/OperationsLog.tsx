/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {Alert, Heading, PageLayout, Spinner} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {isSpecificTenant} from '#/operate/shared/utils/isSpecificTenant';
import type {OperationsLogSearch} from '../operationsLog.schema';
import {resolvedDefinitionQuery, selectedDefinitionsQuery} from '#/operate/shared/queries/processDefinitions.queries';
import {Filters} from './Filters';
import {InstancesTable} from './InstancesTable';

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
		...(search.version === undefined
			? selectedDefinitionsQuery(search.process ?? '', search.tenantId)
			: resolvedDefinitionQuery(search.process ?? '', search.tenantId, search.version)),
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
		(matches !== undefined &&
			matches.length > 0 &&
			(search.version === undefined || (tenantId !== undefined && selectedDefinition !== undefined)));

	return (
		<PageLayout id="main-content" tabIndex={-1} padding="none" width="full" className="h-full">
			<div className="flex h-full min-h-0 flex-1 overflow-hidden">
				<Heading as="h1" className="sr-only">
					{t('operate.operationsLog.title')}
				</Heading>
				<Filters search={search} />
				<div className="min-h-0 min-w-0 flex-1 overflow-hidden">
					{needsLookup && isPending ? (
						<Spinner className="p-4" label={t('operate.operationsLog.filters.resolvingDefinition')} />
					) : needsLookup && isError ? (
						<div className="p-4">
							<Alert
								variant="destructive"
								role="alert"
								title={t('operate.operationsLog.filters.definitionLookupFailed')}
							/>
						</div>
					) : !isResolved ? (
						<div className="p-4">
							<Alert variant="warning" title={t('operate.operationsLog.filters.definitionUnavailable')} />
						</div>
					) : (
						<InstancesTable
							search={search}
							selectedTenantId={
								search.process ? tenantId : isSpecificTenant(search.tenantId) ? search.tenantId : undefined
							}
							selectedDefinitionKey={selectedDefinition?.processDefinitionKey}
						/>
					)}
				</div>
			</div>
		</PageLayout>
	);
};

export {OperationsLog};
