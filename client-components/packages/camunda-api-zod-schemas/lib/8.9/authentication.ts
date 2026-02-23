/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from './common';

const currentUserSchema = z.object({
	username: z.string(),
	displayName: z.string(),
	email: z.string(),
	authorizedComponents: z.array(z.string()),
	tenants: z.array(
		z.object({
			tenantId: z.string(),
			name: z.string(),
			description: z.string().nullable(),
		}),
	),
	groups: z.array(z.string()),
	roles: z.array(z.string()),
	salesPlanType: z.string().nullable(),
	c8Links: z.record(z.string(), z.string()),
	canLogout: z.boolean(),
});

type CurrentUser = z.infer<typeof currentUserSchema>;

const getCurrentUser: Endpoint = {
	method: 'GET',
	getUrl: () => `/${API_VERSION}/authentication/me`,
};

export {currentUserSchema, getCurrentUser};
export type {CurrentUser};
