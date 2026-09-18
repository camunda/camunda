/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions, useQuery} from '@tanstack/react-query';
import type {BatchOperation} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';

function getBatchOperationOptions(batchOperationKey: string) {
	return queryOptions({
		queryKey: ['batchOperation', batchOperationKey] as const,
		queryFn: async (): Promise<BatchOperation> => {
			const {response, error} = await request(endpoints.getBatchOperation({batchOperationKey}));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json();
		},
	});
}

function useBatchOperation(batchOperationKey: string) {
	return useQuery(getBatchOperationOptions(batchOperationKey));
}

export {getBatchOperationOptions, useBatchOperation};
