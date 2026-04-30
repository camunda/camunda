/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, getQueryResponseBodySchema, getQueryRequestBodySchema, type Endpoint} from '../common';

const permissionTypeSchema = z.enum([
	'ACCESS',
	'CREATE',
	'CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE',
	'CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE',
	'CREATE_BATCH_OPERATION_MIGRATE_PROCESS_INSTANCE',
	'CREATE_BATCH_OPERATION_MODIFY_PROCESS_INSTANCE',
	'CREATE_BATCH_OPERATION_RESOLVE_INCIDENT',
	'CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE',
	'CREATE_BATCH_OPERATION_DELETE_DECISION_DEFINITION',
	'CREATE_BATCH_OPERATION_DELETE_PROCESS_DEFINITION',
	'CREATE_PROCESS_INSTANCE',
	'CREATE_DECISION_INSTANCE',
	'READ',
	'READ_PROCESS_INSTANCE',
	'READ_USER_TASK',
	'READ_DECISION_INSTANCE',
	'READ_PROCESS_DEFINITION',
	'READ_DECISION_DEFINITION',
	'UPDATE',
	'UPDATE_PROCESS_INSTANCE',
	'UPDATE_USER_TASK',
	'DELETE',
	'DELETE_PROCESS',
	'DELETE_DRD',
	'DELETE_FORM',
	'DELETE_RESOURCE',
	'DELETE_PROCESS_INSTANCE',
	'DELETE_DECISION_INSTANCE',
]);
type PermissionType = z.infer<typeof permissionTypeSchema>;

const resourceTypeSchema = z.enum([
	'AUTHORIZATION',
	'MAPPING_RULE',
	'MESSAGE',
	'BATCH',
	'BATCH_OPERATION',
	'APPLICATION',
	'SYSTEM',
	'TENANT',
	'RESOURCE',
	'PROCESS_DEFINITION',
	'DECISION_REQUIREMENTS_DEFINITION',
	'DECISION_DEFINITION',
	'GROUP',
	'USER',
	'ROLE',
]);
type ResourceType = z.infer<typeof resourceTypeSchema>;

const ownerTypeSchema = z.enum(['USER', 'CLIENT', 'ROLE', 'GROUP', 'MAPPING', 'UNSPECIFIED']);
type OwnerType = z.infer<typeof ownerTypeSchema>;

const authorizationSchema = z.object({
	ownerId: z.string(),
	ownerType: ownerTypeSchema,
	resourceType: resourceTypeSchema,
	resourceId: z.string(),
	permissionTypes: z.array(permissionTypeSchema),
	authorizationKey: z.string(),
});
type Authorization = z.infer<typeof authorizationSchema>;

const createAuthorizationRequestBodySchema = authorizationSchema.pick({
	ownerId: true,
	ownerType: true,
	resourceType: true,
	resourceId: true,
	permissionTypes: true,
});
type CreateAuthorizationRequestBody = z.infer<typeof createAuthorizationRequestBodySchema>;

const updateAuthorizationRequestBodySchema = createAuthorizationRequestBodySchema.partial();
type UpdateAuthorizationRequestBody = z.infer<typeof updateAuthorizationRequestBodySchema>;

const queryAuthorizationsRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['ownerId', 'ownerType', 'resourceId', 'resourceType'] as const,
	filter: z
		.object({
			resourceId: z.array(z.string()),
			...authorizationSchema.pick({
				ownerId: true,
				ownerType: true,
				resourceType: true,
			}).shape,
		})
		.partial(),
});
type QueryAuthorizationsRequestBody = z.infer<typeof queryAuthorizationsRequestBodySchema>;

const queryAuthorizationsResponseBodySchema = getQueryResponseBodySchema(authorizationSchema);
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
