/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions, useQuery} from '@tanstack/react-query';
import type {GetDecisionInstanceResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';

function decisionInstanceQuery(decisionEvaluationInstanceKey: string) {
	return queryOptions({
		queryKey: ['decisionInstance', decisionEvaluationInstanceKey] as const,
		queryFn: async (): Promise<GetDecisionInstanceResponseBody> => {
			const {response, error} = await request(endpoints.getDecisionInstance({decisionEvaluationInstanceKey}));
			if (error !== null) {
				throw error;
			}
			return response.json();
		},
	});
}

function useDecisionInstance(decisionEvaluationInstanceKey: string) {
	return useQuery(decisionInstanceQuery(decisionEvaluationInstanceKey));
}

export {decisionInstanceQuery, useDecisionInstance};
