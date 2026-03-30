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
	userResultSchema,
	userRequestSchema,
	userUpdateRequestSchema,
	userSearchQueryRequestSchema,
	userSearchResultSchema,
} from './gen';

const userSchema = userResultSchema;
type User = z.infer<typeof userSchema>;

const createUserRequestBodySchema = userRequestSchema;
type CreateUserRequestBody = z.infer<typeof createUserRequestBodySchema>;

const createUserResponseBodySchema = userResultSchema;
type CreateUserResponseBody = z.infer<typeof createUserResponseBodySchema>;

const updateUserRequestBodySchema = userUpdateRequestSchema;
type UpdateUserRequestBody = z.infer<typeof updateUserRequestBodySchema>;

const queryUsersRequestBodySchema = userSearchQueryRequestSchema;
type QueryUsersRequestBody = z.infer<typeof queryUsersRequestBodySchema>;

const queryUsersResponseBodySchema = userSearchResultSchema;
type QueryUsersResponseBody = z.infer<typeof queryUsersResponseBodySchema>;

const createUser: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/users`;
	},
};

const queryUsers: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/users/search`;
	},
};

const getUser: Endpoint<{username: string}> = {
	method: 'GET',
	getUrl(params) {
		const {username} = params;

		return `/${API_VERSION}/users/${username}`;
	},
};

const deleteUser: Endpoint<{username: string}> = {
	method: 'DELETE',
	getUrl(params) {
		const {username} = params;

		return `/${API_VERSION}/users/${username}`;
	},
};

const updateUser: Endpoint<{username: string}> = {
	method: 'PUT',
	getUrl(params) {
		const {username} = params;

		return `/${API_VERSION}/users/${username}`;
	},
};

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
	queryUsersRequestBodySchema,
	queryUsersResponseBodySchema,
};
export type {
	User,
	CreateUserRequestBody,
	CreateUserResponseBody,
	UpdateUserRequestBody,
	QueryUsersRequestBody,
	QueryUsersResponseBody,
};
