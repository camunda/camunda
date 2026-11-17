/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {getUserStatus200Schema} from './gen/zod/getUserSchema';
import {userCreateResultSchema} from './gen/zod/userCreateResultSchema';
import {userRequestSchema} from './gen/zod/userRequestSchema';
import {userResultSchema} from './gen/zod/userResultSchema';
import {userSearchQueryRequestSchema} from './gen/zod/userSearchQueryRequestSchema';
import {userSearchResultSchema} from './gen/zod/userSearchResultSchema';
import {userUpdateRequestSchema} from './gen/zod/userUpdateRequestSchema';
import {userUpdateResultSchema} from './gen/zod/userUpdateResultSchema';
import type {GetUserStatus200} from './gen/types/GetUser';
import type {UserCreateResult} from './gen/types/UserCreateResult';
import type {UserRequest} from './gen/types/UserRequest';
import type {UserResult} from './gen/types/UserResult';
import type {UserSearchQueryRequest} from './gen/types/UserSearchQueryRequest';
import type {UserSearchResult} from './gen/types/UserSearchResult';
import type {UserUpdateRequest} from './gen/types/UserUpdateRequest';
import type {UserUpdateResult} from './gen/types/UserUpdateResult';

const userSchema = userResultSchema;
type User = UserResult;

const createUserRequestBodySchema = userRequestSchema;
type CreateUserRequestBody = UserRequest;

const createUserResponseBodySchema = userCreateResultSchema;
type CreateUserResponseBody = UserCreateResult;

const updateUserRequestBodySchema = userUpdateRequestSchema;
type UpdateUserRequestBody = UserUpdateRequest;

const updateUserResponseBodySchema = userUpdateResultSchema;
type UpdateUserResponseBody = UserUpdateResult;

const getUserResponseBodySchema = getUserStatus200Schema;
type GetUserResponseBody = GetUserStatus200;

const queryUsersRequestBodySchema = userSearchQueryRequestSchema;
type QueryUsersRequestBody = UserSearchQueryRequest;

const queryUsersResponseBodySchema = userSearchResultSchema;
type QueryUsersResponseBody = UserSearchResult;

const createUser = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/users` as const;
	},
} as const satisfies Endpoint;

const queryUsers = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/users/search` as const;
	},
} as const satisfies Endpoint;

const getUser = {
	method: 'GET',
	getUrl(params) {
		const {username} = params;

		return `/${API_VERSION}/users/${username}` as const;
	},
} as const satisfies Endpoint<Pick<User, 'username'>>;

const deleteUser = {
	method: 'DELETE',
	getUrl(params) {
		const {username} = params;

		return `/${API_VERSION}/users/${username}` as const;
	},
} as const satisfies Endpoint<Pick<User, 'username'>>;

const updateUser = {
	method: 'PUT',
	getUrl(params) {
		const {username} = params;

		return `/${API_VERSION}/users/${username}` as const;
	},
} as const satisfies Endpoint<Pick<User, 'username'>>;

export {
	createUser,
	queryUsers,
	getUser,
	deleteUser,
	updateUser,
	userSchema,
	createUserRequestBodySchema,
	createUserResponseBodySchema,
	updateUserRequestBodySchema,
	updateUserResponseBodySchema,
	getUserResponseBodySchema,
	queryUsersRequestBodySchema,
	queryUsersResponseBodySchema,
};
export type {
	User,
	CreateUserRequestBody,
	CreateUserResponseBody,
	UpdateUserRequestBody,
	UpdateUserResponseBody,
	GetUserResponseBody,
	QueryUsersRequestBody,
	QueryUsersResponseBody,
};
