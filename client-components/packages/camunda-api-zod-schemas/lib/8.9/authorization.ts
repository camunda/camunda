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

const createAuthorization: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/authorizations`,
};

const updateAuthorization: Endpoint<{authorizationKey: string}> = {
	method: 'PUT',
	getUrl: ({authorizationKey}) => `/${API_VERSION}/authorizations/${authorizationKey}`,
};

const getAuthorization: Endpoint<{authorizationKey: string}> = {
	method: 'GET',
	getUrl: ({authorizationKey}) => `/${API_VERSION}/authorizations/${authorizationKey}`,
};

const deleteAuthorization: Endpoint<{authorizationKey: string}> = {
	method: 'DELETE',
	getUrl: ({authorizationKey}) => `/${API_VERSION}/authorizations/${authorizationKey}`,
};

const queryAuthorizations: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/authorizations/search`,
};

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
