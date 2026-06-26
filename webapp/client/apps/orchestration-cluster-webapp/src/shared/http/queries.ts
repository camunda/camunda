/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {infiniteQueryOptions, queryOptions} from '@tanstack/react-query';
import type {
	GetSystemConfigurationResponseBody,
	CurrentUser,
	License,
	UserTask,
	QueryUserTasksRequestBody,
	QueryUserTasksResponseBody,
	QueryProcessDefinitionsRequestBody,
	QueryProcessDefinitionsResponseBody,
	GetProcessDefinitionInstanceStatisticsRequestBody,
	GetIncidentProcessInstanceStatisticsByErrorRequestBody,
	GetProcessDefinitionInstanceStatisticsResponseBody,
	GetIncidentProcessInstanceStatisticsByErrorResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from './request';
import {endpoints} from './endpoints';

const DEFAULT_MAX_ITEM_PER_PAGE = 50;

const queryKeys = {
	currentUser: () => ['getCurrentUser'] as const,
	systemConfiguration: () => ['systemConfiguration'] as const,
	license: () => ['license'] as const,
	userTasks: (body: QueryUserTasksRequestBody) => ['userTasks', body] as const,
	userTask: (userTaskKey: string) => ['userTask', userTaskKey] as const,
	processDefinitionXml: (processDefinitionKey: string) => ['processDefinitionXml', processDefinitionKey] as const,
	queryProcessDefinitions: (body: QueryProcessDefinitionsRequestBody) => ['queryProcessDefinitions', body] as const,
	getProcessDefinitionInstanceStatistics: (body: GetProcessDefinitionInstanceStatisticsRequestBody) =>
		['getProcessDefinitionInstanceStatistics', body] as const,
	getIncidentProcessInstanceStatisticsByError: (body: GetIncidentProcessInstanceStatisticsByErrorRequestBody) =>
		['getIncidentProcessInstanceStatisticsByError', body] as const,
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
	getLicense: () =>
		queryOptions({
			queryKey: queryKeys.license(),
			queryFn: async (): Promise<License> => {
				const {response, error} = await request(endpoints.getLicense());
				if (error !== null) {
					throw error;
				}
				return response.json();
			},
			staleTime: Infinity,
			gcTime: Infinity,
		}),
	queryUserTasks: (body: QueryUserTasksRequestBody) => {
		const MAX_TASKS_PER_REQUEST = body.page?.limit ?? DEFAULT_MAX_ITEM_PER_PAGE;
		const enhancedBody = {
			...body,
			page: {
				...body.page,
				limit: MAX_TASKS_PER_REQUEST,
			},
		};

		return infiniteQueryOptions({
			queryKey: queryKeys.userTasks(enhancedBody),
			queryFn: async ({pageParam}): Promise<QueryUserTasksResponseBody> => {
				const {response, error} = await request(
					endpoints.queryUserTasks({
						...enhancedBody,
						page: {
							...enhancedBody.page,
							from: pageParam,
						},
					}),
				);
				if (error !== null) {
					throw error;
				}
				return response.json();
			},
			initialPageParam: body.page?.from ?? 0,
			getNextPageParam: (lastPage, _, lastPageParam) => {
				const nextPage = lastPageParam + MAX_TASKS_PER_REQUEST;

				if (nextPage > lastPage.page.totalItems) {
					return undefined;
				}

				return nextPage;
			},
			getPreviousPageParam: (_, __, firstPageParam) => {
				const previousPage = firstPageParam - MAX_TASKS_PER_REQUEST;

				if (previousPage < 0) {
					return undefined;
				}

				return previousPage;
			},
		});
	},

	getUserTask: (userTaskKey: string) =>
		queryOptions({
			queryKey: queryKeys.userTask(userTaskKey),
			queryFn: async (): Promise<UserTask> => {
				const {response, error} = await request(endpoints.getUserTask({userTaskKey}));
				if (error !== null) {
					throw error;
				}
				return response.json();
			},
		}),

	getProcessDefinitionInstanceStatistics: (body: GetProcessDefinitionInstanceStatisticsRequestBody) =>
		queryOptions({
			queryKey: queryKeys.getProcessDefinitionInstanceStatistics(body),
			queryFn: async (): Promise<GetProcessDefinitionInstanceStatisticsResponseBody> => {
				const {response, error} = await request(endpoints.getProcessDefinitionInstanceStatistics(body));
				if (error !== null) {
					throw error;
				}
				return response.json();
			},
		}),

	queryProcessDefinitions: (body: QueryProcessDefinitionsRequestBody) =>
		queryOptions({
			queryKey: queryKeys.queryProcessDefinitions(body),
			queryFn: async (): Promise<QueryProcessDefinitionsResponseBody> => {
				const {response, error} = await request(endpoints.queryProcessDefinitions(body));
				if (error !== null) {
					throw error;
				}
				return response.json();
			},
		}),

	getIncidentProcessInstanceStatisticsByError: (body: GetIncidentProcessInstanceStatisticsByErrorRequestBody) =>
		queryOptions({
			queryKey: queryKeys.getIncidentProcessInstanceStatisticsByError(body),
			queryFn: async (): Promise<GetIncidentProcessInstanceStatisticsByErrorResponseBody> => {
				const {response, error} = await request(endpoints.getIncidentProcessInstanceStatisticsByError(body));
				if (error !== null) {
					throw error;
				}
				return response.json();
			},
		}),
} as const;

export {queries};
