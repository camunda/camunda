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
	roleCreateRequestSchema,
	roleCreateResultSchema,
	roleUpdateRequestSchema,
	roleUpdateResultSchema,
	roleSearchQueryRequestSchema,
	roleSearchQueryResultSchema,
	roleUserSearchQueryRequestSchema,
	roleUserSearchResultSchema,
	roleClientSearchQueryRequestSchema,
	roleClientSearchResultSchema,
	roleGroupSearchQueryRequestSchema,
	roleGroupSearchResultSchema,
	mappingRuleSearchQueryRequestSchema,
	mappingRuleSearchQueryResultSchema,
} from './gen';
import {roleSchema, type Role} from './group-role';

const createRoleRequestBodySchema = roleCreateRequestSchema;
type CreateRoleRequestBody = z.infer<typeof createRoleRequestBodySchema>;

const createRoleResponseBodySchema = roleCreateResultSchema;
type CreateRoleResponseBody = z.infer<typeof createRoleResponseBodySchema>;

const updateRoleRequestBodySchema = roleUpdateRequestSchema;
type UpdateRoleRequestBody = z.infer<typeof updateRoleRequestBodySchema>;

const updateRoleResponseBodySchema = roleUpdateResultSchema;
type UpdateRoleResponseBody = z.infer<typeof updateRoleResponseBodySchema>;

const queryRolesRequestBodySchema = roleSearchQueryRequestSchema;
type QueryRolesRequestBody = z.infer<typeof queryRolesRequestBodySchema>;

const queryRolesResponseBodySchema = roleSearchQueryResultSchema;
type QueryRolesResponseBody = z.infer<typeof queryRolesResponseBodySchema>;

const queryUsersByRoleRequestBodySchema = roleUserSearchQueryRequestSchema;
type QueryUsersByRoleRequestBody = z.infer<typeof queryUsersByRoleRequestBodySchema>;

const queryUsersByRoleResponseBodySchema = roleUserSearchResultSchema;
type QueryUsersByRoleResponseBody = z.infer<typeof queryUsersByRoleResponseBodySchema>;

const queryClientsByRoleRequestBodySchema = roleClientSearchQueryRequestSchema;
type QueryClientsByRoleRequestBody = z.infer<typeof queryClientsByRoleRequestBodySchema>;

const queryClientsByRoleResponseBodySchema = roleClientSearchResultSchema;
type QueryClientsByRoleResponseBody = z.infer<typeof queryClientsByRoleResponseBodySchema>;

const queryGroupsByRoleRequestBodySchema = roleGroupSearchQueryRequestSchema;
type QueryGroupsByRoleRequestBody = z.infer<typeof queryGroupsByRoleRequestBodySchema>;

const queryGroupsByRoleResponseBodySchema = roleGroupSearchResultSchema;
type QueryGroupsByRoleResponseBody = z.infer<typeof queryGroupsByRoleResponseBodySchema>;

const queryMappingRulesByRoleRequestBodySchema = mappingRuleSearchQueryRequestSchema;
type QueryMappingRulesByRoleRequestBody = z.infer<typeof queryMappingRulesByRoleRequestBodySchema>;

const queryMappingRulesByRoleResponseBodySchema = mappingRuleSearchQueryResultSchema;
type QueryMappingRulesByRoleResponseBody = z.infer<typeof queryMappingRulesByRoleResponseBodySchema>;

const createRole: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/roles`;
	},
};

const getRole: Endpoint<{roleId: string}> = {
	method: 'GET',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${roleId}`;
	},
};

const updateRole: Endpoint<{roleId: string}> = {
	method: 'PUT',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${roleId}`;
	},
};

const deleteRole: Endpoint<{roleId: string}> = {
	method: 'DELETE',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${roleId}`;
	},
};

const queryRoles: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/roles/search`;
	},
};

const queryUsersByRole: Endpoint<{roleId: string}> = {
	method: 'POST',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${roleId}/users/search`;
	},
};

const queryClientsByRole: Endpoint<{roleId: string}> = {
	method: 'POST',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${roleId}/clients/search`;
	},
};

const assignUserToRole: Endpoint<{roleId: string; username: string}> = {
	method: 'PUT',
	getUrl(params) {
		const {roleId, username} = params;

		return `/${API_VERSION}/roles/${roleId}/users/${username}`;
	},
};

const unassignUserFromRole: Endpoint<{roleId: string; username: string}> = {
	method: 'DELETE',
	getUrl(params) {
		const {roleId, username} = params;

		return `/${API_VERSION}/roles/${roleId}/users/${username}`;
	},
};

const assignClientToRole: Endpoint<{roleId: string; clientId: string}> = {
	method: 'PUT',
	getUrl(params) {
		const {roleId, clientId} = params;

		return `/${API_VERSION}/roles/${roleId}/clients/${clientId}`;
	},
};

const unassignClientFromRole: Endpoint<{roleId: string; clientId: string}> = {
	method: 'DELETE',
	getUrl(params) {
		const {roleId, clientId} = params;

		return `/${API_VERSION}/roles/${roleId}/clients/${clientId}`;
	},
};

const assignGroupToRole: Endpoint<{roleId: string; groupId: string}> = {
	method: 'PUT',
	getUrl(params) {
		const {roleId, groupId} = params;

		return `/${API_VERSION}/roles/${roleId}/groups/${groupId}`;
	},
};

const unassignGroupFromRole: Endpoint<{roleId: string; groupId: string}> = {
	method: 'DELETE',
	getUrl(params) {
		const {roleId, groupId} = params;

		return `/${API_VERSION}/roles/${roleId}/groups/${groupId}`;
	},
};

const queryGroupsByRole: Endpoint<{roleId: string}> = {
	method: 'POST',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${roleId}/groups/search`;
	},
};

const assignMappingToRole: Endpoint<{roleId: string; mappingRuleId: string}> = {
	method: 'PUT',
	getUrl(params) {
		const {roleId, mappingRuleId} = params;

		return `/${API_VERSION}/roles/${roleId}/mappings/${mappingRuleId}`;
	},
};

const unassignMappingFromRole: Endpoint<{roleId: string; mappingRuleId: string}> = {
	method: 'DELETE',
	getUrl(params) {
		const {roleId, mappingRuleId} = params;

		return `/${API_VERSION}/roles/${roleId}/mappings/${mappingRuleId}`;
	},
};

const queryMappingRulesByRole: Endpoint<{roleId: string}> = {
	method: 'POST',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${roleId}/mapping-rules/search`;
	},
};

export {
	createRole,
	getRole,
	updateRole,
	deleteRole,
	queryRoles,
	queryUsersByRole,
	queryClientsByRole,
	assignUserToRole,
	unassignUserFromRole,
	assignClientToRole,
	unassignClientFromRole,
	assignGroupToRole,
	unassignGroupFromRole,
	queryGroupsByRole,
	assignMappingToRole,
	unassignMappingFromRole,
	queryMappingRulesByRole,
	roleSchema,
	createRoleRequestBodySchema,
	createRoleResponseBodySchema,
	updateRoleRequestBodySchema,
	updateRoleResponseBodySchema,
	queryRolesRequestBodySchema,
	queryRolesResponseBodySchema,
	queryUsersByRoleRequestBodySchema,
	queryUsersByRoleResponseBodySchema,
	queryClientsByRoleRequestBodySchema,
	queryClientsByRoleResponseBodySchema,
	queryGroupsByRoleRequestBodySchema,
	queryGroupsByRoleResponseBodySchema,
	queryMappingRulesByRoleRequestBodySchema,
	queryMappingRulesByRoleResponseBodySchema,
};
export type {
	Role,
	CreateRoleRequestBody,
	CreateRoleResponseBody,
	UpdateRoleRequestBody,
	UpdateRoleResponseBody,
	QueryRolesRequestBody,
	QueryRolesResponseBody,
	QueryUsersByRoleRequestBody,
	QueryUsersByRoleResponseBody,
	QueryClientsByRoleRequestBody,
	QueryClientsByRoleResponseBody,
	QueryGroupsByRoleRequestBody,
	QueryGroupsByRoleResponseBody,
	QueryMappingRulesByRoleRequestBody,
	QueryMappingRulesByRoleResponseBody,
};
