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

export {decisionDefinitionsOptions};
