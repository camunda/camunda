/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {authorizationCreateResultSchema} from './gen/zod/authorizationCreateResultSchema';
import {authorizationRequestSchema} from './gen/zod/authorizationRequestSchema';
import {authorizationResultSchema} from './gen/zod/authorizationResultSchema';
import {authorizationSearchQuerySchema} from './gen/zod/authorizationSearchQuerySchema';
import {authorizationSearchResultSchema} from './gen/zod/authorizationSearchResultSchema';
import {getAuthorizationStatus200Schema} from './gen/zod/getAuthorizationSchema';
import {ownerTypeEnumSchema} from './gen/zod/ownerTypeEnumSchema';
import {permissionTypeEnumSchema} from './gen/zod/permissionTypeEnumSchema';
import {resourceTypeEnumSchema} from './gen/zod/resourceTypeEnumSchema';
import type {AuthorizationCreateResult} from './gen/types/AuthorizationCreateResult';
import type {AuthorizationRequest} from './gen/types/AuthorizationRequest';
import type {AuthorizationResult} from './gen/types/AuthorizationResult';
import type {AuthorizationSearchQuery} from './gen/types/AuthorizationSearchQuery';
import type {AuthorizationSearchResult} from './gen/types/AuthorizationSearchResult';
import type {GetAuthorizationStatus200} from './gen/types/GetAuthorization';
import type {OwnerTypeEnumKey} from './gen/types/OwnerTypeEnum';
import type {PermissionTypeEnumKey} from './gen/types/PermissionTypeEnum';
import type {ResourceTypeEnumKey} from './gen/types/ResourceTypeEnum';

const permissionTypeSchema = permissionTypeEnumSchema;
type PermissionType = PermissionTypeEnumKey;

const resourceTypeSchema = resourceTypeEnumSchema;
type ResourceType = ResourceTypeEnumKey;

const ownerTypeSchema = ownerTypeEnumSchema;
type OwnerType = OwnerTypeEnumKey;

const authorizationSchema = authorizationResultSchema;
type Authorization = AuthorizationResult;

const createAuthorizationRequestBodySchema = authorizationRequestSchema;
type CreateAuthorizationRequestBody = AuthorizationRequest;

const createAuthorizationResponseBodySchema = authorizationCreateResultSchema;
type CreateAuthorizationResponseBody = AuthorizationCreateResult;

const updateAuthorizationRequestBodySchema = authorizationRequestSchema;
type UpdateAuthorizationRequestBody = AuthorizationRequest;

const queryAuthorizationsRequestBodySchema = authorizationSearchQuerySchema;
type QueryAuthorizationsRequestBody = AuthorizationSearchQuery;

const getAuthorizationResponseBodySchema = getAuthorizationStatus200Schema;
type GetAuthorizationResponseBody = GetAuthorizationStatus200;

const queryAuthorizationsResponseBodySchema = authorizationSearchResultSchema;
type QueryAuthorizationsResponseBody = AuthorizationSearchResult;

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
	createAuthorizationResponseBodySchema,
	updateAuthorizationRequestBodySchema,
	getAuthorizationResponseBodySchema,
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
	CreateAuthorizationResponseBody,
	UpdateAuthorizationRequestBody,
	GetAuthorizationResponseBody,
	QueryAuthorizationsRequestBody,
	QueryAuthorizationsResponseBody,
};
