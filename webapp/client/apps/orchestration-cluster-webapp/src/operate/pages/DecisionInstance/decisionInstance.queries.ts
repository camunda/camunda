/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions, useQuery} from '@tanstack/react-query';
import type {GetDecisionInstanceResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {request, requestErrorSchema} from '#/shared/http/request';
import {ForbiddenError} from '#/shared/errors';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';

function decisionInstanceQuery(decisionEvaluationInstanceKey: string) {
	return queryOptions({
		queryKey: ['decisionInstance', decisionEvaluationInstanceKey] as const,
		queryFn: async (): Promise<GetDecisionInstanceResponseBody> => {
			const {response, error} = await request(endpoints.getDecisionInstance({decisionEvaluationInstanceKey}));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json();
		},
	});
}

function useDecisionInstance(decisionEvaluationInstanceKey: string) {
	const query = useQuery(decisionInstanceQuery(decisionEvaluationInstanceKey));
	const requestError = requestErrorSchema.safeParse(query.error);
	const isUnauthorized = query.error instanceof ForbiddenError;
	const isNotFound = requestError.success && requestError.data.response?.status === 404;

	return {
		query,
		isUnauthorized,
		isNotFound,
		isGenericError: query.isError && !isUnauthorized && !isNotFound,
	};
}

export {decisionInstanceQuery, useDecisionInstance};
