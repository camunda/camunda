/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions} from '@tanstack/react-query';
import type {QueryDecisionDefinitionsResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';
import {queries} from '#/shared/http/queries';

type DecisionDefinitionSelectionOptions = {
	decisionDefinitionId?: string;
	decisionDefinitionVersion?: number;
	tenantId?: string;
};

function decisionDefinitionsOptions(tenantId?: string) {
	return queryOptions({
		queryKey: ['decisionDefinitions', tenantId] as const,
		queryFn: async (): Promise<QueryDecisionDefinitionsResponseBody> => {
			const {response, error} = await request(
				endpoints.queryDecisionDefinitions({page: {limit: 1000}, filter: tenantId ? {tenantId} : undefined}),
			);
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json();
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
			sort: [{field: 'version', order: 'desc'}],
		}),
		staleTime: 5000,
	};
}

export {decisionDefinitionsOptions, decisionDefinitionSelectionOptions};
