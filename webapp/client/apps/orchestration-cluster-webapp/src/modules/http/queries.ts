/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions} from '@tanstack/react-query';
import type {GetSystemConfigurationResponseBody, CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from './request';
import {endpoints} from './endpoints';

const queryKeys = {
	currentUser: () => ['getCurrentUser'] as const,
	systemConfiguration: () => ['systemConfiguration'] as const,
};

const queries = {
	getCurrentUser: () =>
		queryOptions({
			queryKey: queryKeys.currentUser(),
			queryFn: async (): Promise<CurrentUser> => {
				const {response, error} = await request(endpoints.getCurrentUser());
				if (error !== null) {
					throw error;
				}
				return response.json();
			},
			staleTime: Infinity,
			gcTime: Infinity,
			retry: false,
		}),
	getSystemConfiguration: () =>
		queryOptions({
			queryKey: queryKeys.systemConfiguration(),
			queryFn: async (): Promise<GetSystemConfigurationResponseBody> => {
				const {response, error} = await request(endpoints.getSystemConfiguration());
				if (error !== null) {
					throw error;
				}
				return response.json();
			},
			staleTime: Infinity,
			gcTime: Infinity,
		}),
} as const;

export {queries};
