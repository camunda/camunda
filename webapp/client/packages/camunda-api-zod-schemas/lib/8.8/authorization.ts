/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {
	permissionTypeEnumSchema,
	resourceTypeEnumSchema,
	ownerTypeEnumSchema,
	authorizationResultSchema,
	authorizationRequestSchema,
	authorizationSearchQuerySchema,
	authorizationSearchResultSchema,
} from './gen';

const permissionTypeSchema = permissionTypeEnumSchema;
type PermissionType = z.infer<typeof permissionTypeSchema>;

const resourceTypeSchema = resourceTypeEnumSchema;
type ResourceType = z.infer<typeof resourceTypeSchema>;

const ownerTypeSchema = ownerTypeEnumSchema;
type OwnerType = z.infer<typeof ownerTypeSchema>;

const authorizationSchema = authorizationResultSchema;
type Authorization = z.infer<typeof authorizationSchema>;

const createAuthorizationRequestBodySchema = authorizationRequestSchema;
type CreateAuthorizationRequestBody = z.infer<typeof createAuthorizationRequestBodySchema>;

const updateAuthorizationRequestBodySchema = authorizationRequestSchema;
type UpdateAuthorizationRequestBody = z.infer<typeof updateAuthorizationRequestBodySchema>;

const queryAuthorizationsRequestBodySchema = authorizationSearchQuerySchema;
type QueryAuthorizationsRequestBody = z.infer<typeof queryAuthorizationsRequestBodySchema>;

const queryAuthorizationsResponseBodySchema = authorizationSearchResultSchema;
type QueryAuthorizationsResponseBody = z.infer<typeof queryAuthorizationsResponseBodySchema>;

const createAuthorization = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/authorizations` as const,
} as const satisfies Endpoint;

const updateAuthorization = {
	method: 'PUT',
	getUrl: ({authorizationKey}) => `/${API_VERSION}/authorizations/${authorizationKey}` as const,
} as const satisfies Endpoint<{authorizationKey: string}>;

const getAuthorization = {
	method: 'GET',
	getUrl: ({authorizationKey}) => `/${API_VERSION}/authorizations/${authorizationKey}` as const,
} as const satisfies Endpoint<{authorizationKey: string}>;

const deleteAuthorization = {
	method: 'DELETE',
	getUrl: ({authorizationKey}) => `/${API_VERSION}/authorizations/${authorizationKey}` as const,
} as const satisfies Endpoint<{authorizationKey: string}>;

const queryAuthorizations = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/authorizations/search` as const,
} as const satisfies Endpoint;

export {
	permissionTypeSchema,
	resourceTypeSchema,
	ownerTypeSchema,
	authorizationSchema,
	createAuthorizationRequestBodySchema,
	updateAuthorizationRequestBodySchema,
	queryAuthorizationsRequestBodySchema,
	queryAuthorizationsResponseBodySchema,
	createAuthorization,
	updateAuthorization,
	getAuthorization,
	deleteAuthorization,
	queryAuthorizations,
};

export type {
	PermissionType,
	ResourceType,
	OwnerType,
	Authorization,
	CreateAuthorizationRequestBody,
	UpdateAuthorizationRequestBody,
	QueryAuthorizationsRequestBody,
	QueryAuthorizationsResponseBody,
};
