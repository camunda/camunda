/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions, useQuery} from '@tanstack/react-query';
import type {GetDecisionInstanceResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
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

function getDecisionInstanceErrorKind(error: unknown): 'forbidden' | 'notFound' | 'generic' {
	if (error instanceof ForbiddenError) {
		return 'forbidden';
	}
	const requestError = requestErrorSchema.safeParse(error);
	return requestError.success && requestError.data.response?.status === 404 ? 'notFound' : 'generic';
}

function useDecisionInstance(decisionEvaluationInstanceKey: string) {
	const query = useQuery(decisionInstanceQuery(decisionEvaluationInstanceKey));
	const errorKind = query.isError ? getDecisionInstanceErrorKind(query.error) : null;

	return {
		query,
		isUnauthorized: errorKind === 'forbidden',
		isNotFound: errorKind === 'notFound',
		isGenericError: errorKind === 'generic',
	};
}

export {decisionInstanceQuery, getDecisionInstanceErrorKind, useDecisionInstance};
