/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {infiniteQueryOptions, queryOptions, skipToken} from '@tanstack/react-query';
import type {QueryVariablesResponseBody, Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {instanceRequest} from './processInstance.queries';

const VARIABLES_PAGE_SIZE = 50;

function variablesQuery({
	processInstanceKey,
	scopeKey,
	searchTerm = '',
}: {
	processInstanceKey: string;
	scopeKey: string | null;
	searchTerm?: string;
}) {
	const trimmedSearchTerm = searchTerm.trim();
	const name = trimmedSearchTerm === '' ? undefined : `*${trimmedSearchTerm}*`;

	return infiniteQueryOptions({
		queryKey: ['processInstanceVariables', processInstanceKey, scopeKey, name] as const,
		queryFn:
			scopeKey === null
				? skipToken
				: ({pageParam, signal}) =>
						instanceRequest<QueryVariablesResponseBody>(
							endpoints.queryVariables({
								filter: {processInstanceKey, scopeKey, ...(name === undefined ? {} : {name: {$like: name}})},
								page: {from: pageParam, limit: VARIABLES_PAGE_SIZE},
								sort: [{field: 'name', order: 'ASC'}],
							}),
							signal,
						),
		initialPageParam: 0,
		getNextPageParam: (lastPage, _, lastPageParam) => {
			const nextPageParam = lastPageParam + VARIABLES_PAGE_SIZE;
			return nextPageParam < lastPage.page.totalItems ? nextPageParam : undefined;
		},
	});
}

function variableQuery(variableKey: string) {
	return queryOptions({
		queryKey: ['variable', variableKey] as const,
		queryFn: ({signal}) => instanceRequest<Variable>(endpoints.getVariable({variableKey}), signal),
		staleTime: 5000,
	});
}

export {variablesQuery, variableQuery};
