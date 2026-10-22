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
const DOCUMENT_VALUE_FILTER = '*camunda.document.type*';

const variableQueryKeys = {
	instanceVariables: (processInstanceKey: string) => ['processInstanceVariables', processInstanceKey] as const,
	variableValues: () => ['variable'] as const,
};

function variablesQuery({
	processInstanceKey,
	scopeKey,
	searchTerm = '',
	documentsOnly = false,
}: {
	processInstanceKey: string;
	scopeKey: string | null;
	searchTerm?: string;
	documentsOnly?: boolean;
}) {
	const trimmedSearchTerm = searchTerm.trim();
	const name = trimmedSearchTerm === '' ? undefined : `*${trimmedSearchTerm}*`;
	const value = documentsOnly ? DOCUMENT_VALUE_FILTER : undefined;

	return infiniteQueryOptions({
		queryKey: [...variableQueryKeys.instanceVariables(processInstanceKey), scopeKey, name, value] as const,
		queryFn:
			scopeKey === null
				? skipToken
				: ({pageParam, signal}) =>
						instanceRequest<QueryVariablesResponseBody>(
							endpoints.queryVariables({
								filter: {
									processInstanceKey,
									scopeKey,
									...(name === undefined ? {} : {name: {$like: name}}),
									...(value === undefined ? {} : {value: {$like: value}}),
								},
								page:
									pageParam === null ? {limit: VARIABLES_PAGE_SIZE} : {after: pageParam, limit: VARIABLES_PAGE_SIZE},
								sort: [{field: 'name', order: 'ASC'}],
							}),
							signal,
						),
		initialPageParam: null as string | null,
		getNextPageParam: (lastPage, allPages) => {
			const loadedItems = new Set(allPages.flatMap(({items}) => items.map(({variableKey}) => variableKey))).size;
			const hasMoreItems = lastPage.page.hasMoreTotalItems || loadedItems < lastPage.page.totalItems;
			return lastPage.items.length === 0 || lastPage.page.endCursor === null || !hasMoreItems
				? undefined
				: lastPage.page.endCursor;
		},
	});
}

function variableQuery(variableKey: string) {
	return queryOptions({
		queryKey: [...variableQueryKeys.variableValues(), variableKey] as const,
		queryFn: ({signal}) => instanceRequest<Variable>(endpoints.getVariable({variableKey}), signal),
		staleTime: 5000,
	});
}

export {VARIABLES_PAGE_SIZE, variableQueryKeys, variablesQuery, variableQuery};
