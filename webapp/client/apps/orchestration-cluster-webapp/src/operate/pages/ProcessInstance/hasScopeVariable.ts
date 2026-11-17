/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryClient} from '@tanstack/react-query';
import type {QueryVariablesResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {instanceRequest} from './processInstance.queries';

async function hasScopeVariable(
	queryClient: QueryClient,
	{processInstanceKey, scopeKey, name}: {processInstanceKey: string; scopeKey: string; name: string},
) {
	const {items} = await queryClient.fetchQuery({
		queryKey: ['scopeVariable', processInstanceKey, scopeKey, name],
		queryFn: ({signal}) =>
			instanceRequest<QueryVariablesResponseBody>(
				endpoints.queryVariables({filter: {name, processInstanceKey, scopeKey}, page: {limit: 1}}),
				signal,
			),
		staleTime: 0,
	});

	return items.some((item) => item.name === name);
}

export {hasScopeVariable};
