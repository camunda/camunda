/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions} from '@tanstack/react-query';
import type {DecisionDefinition} from '@camunda/camunda-api-zod-schemas/8.11';
import {queries} from '#/shared/http/queries';

const PAGE_LIMIT = 1000;

type DecisionDefinitionSelectionOptions = {
	decisionDefinitionId?: string;
	decisionDefinitionVersion?: number;
	tenantId?: string;
};

function decisionDefinitionsOptions(tenantId?: string) {
	return queryOptions({
		queryKey: ['decisionDefinitions', tenantId] as const,
		queryFn: async ({client}): Promise<DecisionDefinition[]> => {
			const filter = {isLatestVersion: true, ...(tenantId ? {tenantId} : {})};
			const fetchFrom = async (after?: string): Promise<DecisionDefinition[]> => {
				const {items, page} = await client.fetchQuery(
					queries.queryDecisionDefinitions({filter, page: {limit: PAGE_LIMIT, after}}),
				);
				return items.length === 0 || page.endCursor === null || page.endCursor === after
					? items
					: [...items, ...(await fetchFrom(page.endCursor))];
			};
			return fetchFrom();
		},
	});
}

function decisionDefinitionSelectionOptions({
	decisionDefinitionId,
	decisionDefinitionVersion,
	tenantId,
}: DecisionDefinitionSelectionOptions) {
	return {
		...queries.queryDecisionDefinitions({
			filter: {
				decisionDefinitionId,
				version: decisionDefinitionVersion,
				tenantId,
			},
			page: {limit: decisionDefinitionVersion === undefined ? 1 : 2},
			sort: [{field: 'version', order: 'DESC'}],
		}),
		staleTime: 5000,
	};
}

export {decisionDefinitionsOptions, decisionDefinitionSelectionOptions};
