/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions} from '@tanstack/react-query';
import type {ProcessDefinition, QueryProcessDefinitionsRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {queries} from '#/shared/http/queries';

const PAGE_LIMIT = 1000;
type DefinitionQueryOptions = {retry?: false; requireComplete?: true};

const operationsLogDefinitionsQuery = (
	filter: NonNullable<QueryProcessDefinitionsRequestBody['filter']>,
	options?: DefinitionQueryOptions,
) =>
	queryOptions({
		queryKey: [
			'operationsLogDefinitions',
			filter,
			...(options?.retry === false ? ['no-retry'] : []),
			...(options?.requireComplete === true ? ['require-complete'] : []),
		] as const,
		queryFn: async ({client, signal}): Promise<ProcessDefinition[]> => {
			const items: ProcessDefinition[] = [];
			let after: string | undefined;
			let hasMore = true;

			while (hasMore) {
				signal.throwIfAborted();
				const page = await client.fetchQuery({
					...queries.queryProcessDefinitions({filter, page: {limit: PAGE_LIMIT, after}}),
					...(options?.retry === false ? {retry: false} : {}),
				});
				items.push(...page.items);
				const next = page.page.endCursor;
				if (next === null) {
					// A later empty cursor page proves exhaustion even if the total hit count is capped.
					if (
						options?.requireComplete === true &&
						after !== undefined &&
						page.items.length === 0 &&
						page.page.hasMoreTotalItems
					) {
						break;
					}
					if (
						(options?.requireComplete === true && page.page.hasMoreTotalItems) ||
						(items.length < page.page.totalItems && !page.page.hasMoreTotalItems)
					) {
						throw new Error('Process definition search ended before all results were returned');
					}
					break;
				}
				hasMore =
					page.page.hasMoreTotalItems || items.length < page.page.totalItems || page.items.length === PAGE_LIMIT;
				if (hasMore) {
					if (next === after) {
						throw new Error('Process definition search did not provide a next page cursor');
					}
					after = next;
				}
			}

			return items;
		},
	});

const selectedDefinitionsQuery = (process: string, tenantId?: string, options?: DefinitionQueryOptions) =>
	operationsLogDefinitionsQuery(
		{
			processDefinitionId: {$eq: process},
			...(tenantId && tenantId !== 'all' ? {tenantId} : {}),
		},
		options,
	);

const resolvedDefinitionQuery = (process: string, tenantId: string | undefined, version: number) =>
	operationsLogDefinitionsQuery({
		processDefinitionId: {$eq: process},
		...(tenantId && tenantId !== 'all' ? {tenantId} : {}),
		version,
	});

export {operationsLogDefinitionsQuery, selectedDefinitionsQuery, resolvedDefinitionQuery};
