/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {problemDetailResponseSchema} from '@camunda/camunda-api-zod-schemas/8.11';
import {requestErrorSchema} from '#/shared/http/request';

async function isDuplicateClusterVariableError(error: unknown): Promise<boolean> {
	const requestError = requestErrorSchema.safeParse(error);
	if (
		!requestError.success ||
		requestError.data.variant !== 'failed-response' ||
		requestError.data.response.status !== 409
	) {
		return false;
	}

	const problemDetail = problemDetailResponseSchema.safeParse(await requestError.data.response.clone().json());
	return problemDetail.success && /already exists/i.test(problemDetail.data.detail);
}

export {isDuplicateClusterVariableError};
