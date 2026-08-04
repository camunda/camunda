/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, useQuery} from '@tanstack/react-query';
import type {DecisionDefinition} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';

const PAGE_LIMIT = 1000;

function useDecisionDefinitionVersions(decisionDefinitionId?: string, tenantId?: string) {
	return useQuery({
		queryKey: ['decisionDefinitionVersions', decisionDefinitionId, tenantId] as const,
		queryFn:
			decisionDefinitionId === undefined
				? skipToken
				: async (): Promise<DecisionDefinition[]> => {
						const definitions: DecisionDefinition[] = [];
						let after: string | undefined;

						while (true) {
							const {response, error} = await request(
								endpoints.queryDecisionDefinitions({
									filter: {decisionDefinitionId, tenantId},
									sort: [{field: 'version', order: 'desc'}],
									page: {after, limit: PAGE_LIMIT},
								}),
							);
							if (error !== null) {
								throw error;
							}

							const result = await response.json();
							definitions.push(...result.items);

							const nextCursor = result.page.endCursor ?? undefined;
							if (
								result.items.length === 0 ||
								nextCursor === undefined ||
								nextCursor === after ||
								(!result.page.hasMoreTotalItems && definitions.length >= result.page.totalItems)
							) {
								return definitions;
							}
							after = nextCursor;
						}
					},
		staleTime: 'static',
	});
}

export {useDecisionDefinitionVersions};
