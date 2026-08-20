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
	Form,
	UserTask,
	QueryUserTasksRequestBody,
	QueryUserTasksResponseBody,
	QueryVariablesByUserTaskRequestBody,
	QueryVariablesByUserTaskResponseBody,
	QueryProcessDefinitionsRequestBody,
	QueryProcessDefinitionsResponseBody,
	GetProcessDefinitionInstanceStatisticsRequestBody,
	GetIncidentProcessInstanceStatisticsByErrorRequestBody,
	GetProcessDefinitionInstanceStatisticsResponseBody,
	GetIncidentProcessInstanceStatisticsByErrorResponseBody,
	QueryUserTaskAuditLogsRequestBody,
	QueryUserTaskAuditLogsResponseBody,
	GetAuditLogResponseBody,
	Variable,
	QueryDecisionDefinitionsRequestBody,
	QueryDecisionDefinitionsResponseBody,
	GetProcessDefinitionResponseBody,
	GetProcessStartFormResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from './request';
import {endpoints} from './endpoints';
import {mapQueryError} from './mapQueryError';

const DEFAULT_MAX_ITEM_PER_PAGE = 50;

type ProcessStartFormResponse = Omit<GetProcessStartFormResponseBody, 'schema'> & {
	schema: string;
};

const queryKeys = {
	currentUser: () => ['getCurrentUser'] as const,
	systemConfiguration: () => ['systemConfiguration'] as const,
	license: () => ['license'] as const,
	userTasks: (body: QueryUserTasksRequestBody) => ['userTasks', body] as const,
	userTask: (userTaskKey: string) => ['userTask', userTaskKey] as const,
	userTaskForm: (userTaskKey: string) => ['userTaskForm', userTaskKey] as const,
	userTaskVariables: (userTaskKey: string, body: QueryVariablesByUserTaskRequestBody, truncateValues?: boolean) =>
		['userTaskVariables', userTaskKey, body, truncateValues] as const,
	allUserTaskVariables: (userTaskKey: string) => ['allUserTaskVariables', userTaskKey] as const,
	variable: (variableKey: string) => ['variable', variableKey] as const,
	processDefinitionXml: (processDefinitionKey: string) => ['processDefinitionXml', processDefinitionKey] as const,
	userTaskAuditLogs: (userTaskKey: string, body: QueryUserTaskAuditLogsRequestBody) =>
		['userTaskAuditLogs', userTaskKey, body] as const,
	auditLog: (auditLogKey: string) => ['auditLog', auditLogKey] as const,
	queryProcessDefinitions: (body: QueryProcessDefinitionsRequestBody) => ['queryProcessDefinitions', body] as const,
	queryProcessDefinitionsInfinite: (body: QueryProcessDefinitionsRequestBody) =>
		['queryProcessDefinitionsInfinite', body] as const,
	processDefinition: (processDefinitionKey: string) => ['processDefinition', processDefinitionKey] as const,
	processStartForm: (processDefinitionKey: string) => ['processStartForm', processDefinitionKey] as const,
	queryDecisionDefinitions: (body: QueryDecisionDefinitionsRequestBody) => ['queryDecisionDefinitions', body] as const,
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
					throw mapQueryError(error);
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
					throw mapQueryError(error);
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
					throw mapQueryError(error);
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
					throw mapQueryError(error);
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
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	getUserTaskForm: (userTaskKey: string) =>
		queryOptions({
			queryKey: queryKeys.userTaskForm(userTaskKey),
			queryFn: async (): Promise<Form> => {
				const {response, error} = await request(endpoints.getUserTaskForm({userTaskKey}));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	queryVariablesByUserTask: (
		userTaskKey: string,
		body: QueryVariablesByUserTaskRequestBody,
		options?: {truncateValues?: boolean},
	) =>
		queryOptions({
			queryKey: queryKeys.userTaskVariables(userTaskKey, body, options?.truncateValues),
			queryFn: async (): Promise<QueryVariablesByUserTaskResponseBody> => {
				const {response, error} = await request(
					endpoints.queryVariablesByUserTask({
						userTaskKey,
						truncateValues: options?.truncateValues,
						...body,
					}),
				);
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	queryAllVariablesByUserTask: (userTaskKey: string) =>
		infiniteQueryOptions({
			queryKey: queryKeys.allUserTaskVariables(userTaskKey),
			queryFn: async ({pageParam}): Promise<QueryVariablesByUserTaskResponseBody> => {
				const {response, error} = await request(
					endpoints.queryVariablesByUserTask({
						userTaskKey,
						page: {limit: DEFAULT_MAX_ITEM_PER_PAGE, from: pageParam},
					}),
				);
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
			initialPageParam: 0,
			getNextPageParam: (lastPage, _, lastPageParam) => {
				const nextPage = lastPageParam + DEFAULT_MAX_ITEM_PER_PAGE;
				return nextPage >= lastPage.page.totalItems ? undefined : nextPage;
			},
		}),

	getVariable: (variableKey: string) =>
		queryOptions({
			queryKey: queryKeys.variable(variableKey),
			queryFn: async (): Promise<Variable> => {
				const {response, error} = await request(endpoints.getVariable({variableKey}));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
			retry: false,
		}),

	queryUserTaskAuditLogs: (userTaskKey: string, body: QueryUserTaskAuditLogsRequestBody) => {
		const MAX_AUDIT_LOGS_PER_REQUEST = body.page?.limit ?? DEFAULT_MAX_ITEM_PER_PAGE;
		const enhancedBody = {
			...body,
			page: {
				...body.page,
				limit: MAX_AUDIT_LOGS_PER_REQUEST,
			},
		};

		return infiniteQueryOptions({
			queryKey: queryKeys.userTaskAuditLogs(userTaskKey, enhancedBody),
			queryFn: async ({pageParam}): Promise<QueryUserTaskAuditLogsResponseBody> => {
				const {response, error} = await request(
					endpoints.queryUserTaskAuditLogs({
						userTaskKey,
						...enhancedBody,
						page: {
							...enhancedBody.page,
							from: pageParam,
						},
					}),
				);
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
			initialPageParam: body.page?.from ?? 0,
			getNextPageParam: (lastPage, _, lastPageParam) => {
				const nextPage = lastPageParam + MAX_AUDIT_LOGS_PER_REQUEST;

				if (nextPage >= lastPage.page.totalItems) {
					return undefined;
				}

				return nextPage;
			},
			getPreviousPageParam: (_, __, firstPageParam) => {
				const previousPage = firstPageParam - MAX_AUDIT_LOGS_PER_REQUEST;

				if (previousPage < 0) {
					return undefined;
				}

				return previousPage;
			},
		});
	},

	getAuditLog: (auditLogKey: string) =>
		queryOptions({
			queryKey: queryKeys.auditLog(auditLogKey),
			queryFn: async (): Promise<GetAuditLogResponseBody> => {
				const {response, error} = await request(endpoints.getAuditLog({auditLogKey}));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	getProcessDefinitionXml: (processDefinitionKey: string) =>
		queryOptions({
			queryKey: queryKeys.processDefinitionXml(processDefinitionKey),
			queryFn: async (): Promise<string> => {
				const {response, error} = await request(endpoints.getProcessDefinitionXml({processDefinitionKey}));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.text();
			},
			staleTime: 'static',
		}),

	getProcessDefinitionInstanceStatistics: (body: GetProcessDefinitionInstanceStatisticsRequestBody) =>
		queryOptions({
			queryKey: queryKeys.getProcessDefinitionInstanceStatistics(body),
			queryFn: async (): Promise<GetProcessDefinitionInstanceStatisticsResponseBody> => {
				const {response, error} = await request(endpoints.getProcessDefinitionInstanceStatistics(body));
				if (error !== null) {
					throw mapQueryError(error);
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
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	getProcessDefinition: (processDefinitionKey: string) =>
		queryOptions({
			queryKey: queryKeys.processDefinition(processDefinitionKey),
			queryFn: async (): Promise<GetProcessDefinitionResponseBody> => {
				const {response, error} = await request(endpoints.getProcessDefinition({processDefinitionKey}));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	getProcessStartForm: (processDefinitionKey: string) =>
		queryOptions({
			queryKey: queryKeys.processStartForm(processDefinitionKey),
			queryFn: async (): Promise<ProcessStartFormResponse> => {
				const {response, error} = await request(endpoints.getProcessStartForm({processDefinitionKey}));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	queryProcessDefinitionsInfinite: (body: QueryProcessDefinitionsRequestBody) =>
		infiniteQueryOptions({
			queryKey: queryKeys.queryProcessDefinitionsInfinite(body),
			queryFn: async ({pageParam}): Promise<QueryProcessDefinitionsResponseBody> => {
				const {response, error} = await request(
					endpoints.queryProcessDefinitions({
						...body,
						page: {
							...body.page,
							after: pageParam ?? undefined,
						},
					}),
				);
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
			initialPageParam: null as string | null,
			getNextPageParam: (lastPage) => lastPage.page.endCursor ?? undefined,
		}),

	getIncidentProcessInstanceStatisticsByError: (body: GetIncidentProcessInstanceStatisticsByErrorRequestBody) =>
		queryOptions({
			queryKey: queryKeys.getIncidentProcessInstanceStatisticsByError(body),
			queryFn: async (): Promise<GetIncidentProcessInstanceStatisticsByErrorResponseBody> => {
				const {response, error} = await request(endpoints.getIncidentProcessInstanceStatisticsByError(body));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	queryDecisionDefinitions: (body: QueryDecisionDefinitionsRequestBody) =>
		queryOptions({
			queryKey: queryKeys.queryDecisionDefinitions(body),
			queryFn: async (): Promise<QueryDecisionDefinitionsResponseBody> => {
				const {response, error} = await request(endpoints.queryDecisionDefinitions(body));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),
} as const;

export {queries};
