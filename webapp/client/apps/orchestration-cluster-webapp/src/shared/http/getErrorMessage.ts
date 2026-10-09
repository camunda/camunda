/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {problemDetailResponseSchema} from '@camunda/camunda-api-zod-schemas/8.11';
import {requestErrorSchema} from './request';

async function getErrorMessage(error: unknown): Promise<string | undefined> {
	if (error instanceof Error) {
		return error.message;
	}

	const requestError = requestErrorSchema.safeParse(error);
	if (!requestError.success) {
		return undefined;
	}

	if (requestError.data.variant === 'network-error') {
		return requestError.data.networkError.message;
	}

	const {response} = requestError.data;
	const problemDetails = await response
		.json()
		.then((body: unknown) => problemDetailResponseSchema.safeParse(body))
		.catch(() => undefined);

	return problemDetails?.success ? problemDetails.data.detail : response.statusText || undefined;
}

export {getErrorMessage};
