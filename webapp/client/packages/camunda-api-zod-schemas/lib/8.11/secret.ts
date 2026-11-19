/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {secretListFilterSchema} from './gen/zod/secretListFilterSchema';
import {secretListRequestSchema} from './gen/zod/secretListRequestSchema';
import {secretListResultSchema} from './gen/zod/secretListResultSchema';
import {secretStatusEnumSchema} from './gen/zod/secretStatusEnumSchema';
import type {SecretListFilter} from './gen/types/SecretListFilter';
import type {SecretListRequest} from './gen/types/SecretListRequest';
import type {SecretListResult} from './gen/types/SecretListResult';
import type {SecretStatusEnumKey} from './gen/types/SecretStatusEnum';

const secretStatusSchema = secretStatusEnumSchema;
type SecretStatus = SecretStatusEnumKey;

const listSecretsFilterSchema = secretListFilterSchema;
type ListSecretsFilter = SecretListFilter;

const listSecretsRequestBodySchema = secretListRequestSchema;
type ListSecretsRequestBody = SecretListRequest;

const listSecretsResponseBodySchema = secretListResultSchema;
type ListSecretsResponseBody = SecretListResult;

const listSecrets = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/secrets/list` as const,
} as const satisfies Endpoint;

export {
	secretStatusSchema,
	listSecretsFilterSchema,
	listSecretsRequestBodySchema,
	listSecretsResponseBodySchema,
	listSecrets,
};
export type {SecretStatus, ListSecretsFilter, ListSecretsRequestBody, ListSecretsResponseBody};
