/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {
	API_VERSION,
	getQueryRequestBodySchema,
	getQueryResponseBodySchema,
	advancedStringFilterSchema,
	type Endpoint,
} from './common';

const userSchema = z.object({
	username: z.string(),
	name: z.string(),
	email: z.string(),
});
type User = z.infer<typeof userSchema>;

const createUserRequestBodySchema = userSchema
	.pick({
		username: true,
		name: true,
		email: true,
	})
	.extend({
		password: z.string(),
	});
type CreateUserRequestBody = z.infer<typeof createUserRequestBodySchema>;

const createUserResponseBodySchema = userSchema;
type CreateUserResponseBody = z.infer<typeof createUserResponseBodySchema>;

const updateUserRequestBodySchema = userSchema
	.pick({
		name: true,
		email: true,
	})
	.extend({
		password: z.string(),
	})
	.partial();
type UpdateUserRequestBody = z.infer<typeof updateUserRequestBodySchema>;

const updateUserResponseBodySchema = userSchema;
type UpdateUserResponseBody = z.infer<typeof updateUserResponseBodySchema>;

const queryUsersRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['username', 'name', 'email'] as const,
	filter: z.object({
		username: advancedStringFilterSchema.optional(),
		name: advancedStringFilterSchema.optional(),
		email: advancedStringFilterSchema.optional(),
	}),
});
type QueryUsersRequestBody = z.infer<typeof queryUsersRequestBodySchema>;

const queryUsersResponseBodySchema = getQueryResponseBodySchema(userSchema);
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

const getUser: Endpoint<Pick<User, 'username'>> = {
	method: 'GET',
	getUrl(params) {
		const {username} = params;

		return `/${API_VERSION}/users/${username}`;
	},
};

const getUserResponseBodySchema = userSchema;
type GetUserResponseBody = z.infer<typeof getUserResponseBodySchema>;

const deleteUser: Endpoint<Pick<User, 'username'>> = {
	method: 'DELETE',
	getUrl(params) {
		const {username} = params;

		return `/${API_VERSION}/users/${username}`;
	},
};

const updateUser: Endpoint<Pick<User, 'username'>> = {
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
