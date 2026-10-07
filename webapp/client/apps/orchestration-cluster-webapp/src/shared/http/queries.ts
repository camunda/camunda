/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {infiniteQueryOptions, queryOptions} from '@tanstack/react-query';
import type {
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
	QueryAuditLogsRequestBody,
	QueryAuditLogsResponseBody,
	Variable,
	QueryDecisionDefinitionsRequestBody,
	QueryDecisionDefinitionsResponseBody,
	GetProcessDefinitionResponseBody,
	GetProcessStartFormResponseBody,
	GetSystemConfigurationResponseBody,
	QueryMessageSubscriptionsRequestBody,
	QueryMessageSubscriptionsResponseBody,
	QueryMappingRulesRequestBody,
	QueryMappingRulesResponseBody,
	User,
	QueryUsersRequestBody,
	QueryUsersResponseBody,
	ClusterVariable,
	QueryClusterVariablesRequestBody,
	QueryClusterVariablesResponseBody,
	QueryTenantsRequestBody,
	QueryTenantsResponseBody,
	QueryGlobalTaskListenersRequestBody,
	QueryGlobalTaskListenersResponseBody,
	GlobalTaskListener,
	Authorization,
	QueryAuthorizationsRequestBody,
	QueryAuthorizationsResponseBody,
	QueryRolesRequestBody,
	QueryRolesResponseBody,
	QueryGroupsRequestBody,
	QueryGroupsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {request} from './request';
import {endpoints} from './endpoints';
import {mapQueryError} from './mapQueryError';
import {parseAdminClientConfig, type AdminClientConfig} from './adminClientConfig';

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
	queryMessageSubscriptions: (body: QueryMessageSubscriptionsRequestBody) =>
		['queryMessageSubscriptions', body] as const,
	getProcessDefinitionInstanceStatistics: (body: GetProcessDefinitionInstanceStatisticsRequestBody) =>
		['getProcessDefinitionInstanceStatistics', body] as const,
	getIncidentProcessInstanceStatisticsByError: (body: GetIncidentProcessInstanceStatisticsByErrorRequestBody) =>
		['getIncidentProcessInstanceStatisticsByError', body] as const,
	queryAuditLogs: (body: QueryAuditLogsRequestBody) => ['queryAuditLogs', body] as const,
	queryMappingRules: (body: QueryMappingRulesRequestBody) => ['queryMappingRules', body] as const,
	users: (body: QueryUsersRequestBody) => ['users', body] as const,
	user: (username: string) => ['user', username] as const,
	queryClusterVariables: (body: QueryClusterVariablesRequestBody) => ['queryClusterVariables', body] as const,
	getClusterVariable: (variable: Pick<ClusterVariable, 'name' | 'scope' | 'tenantId'>) =>
		['getClusterVariable', variable] as const,
	queryTenants: (body: QueryTenantsRequestBody) => ['queryTenants', body] as const,
	searchGlobalTaskListeners: (body: QueryGlobalTaskListenersRequestBody) =>
		['searchGlobalTaskListeners', body] as const,
	globalTaskListener: (id: string) => ['globalTaskListener', id] as const,
	queryAuthorizations: (body: QueryAuthorizationsRequestBody) => ['queryAuthorizations', body] as const,
	getAuthorization: (authorization: Pick<Authorization, 'authorizationKey'>) =>
		['getAuthorization', authorization] as const,
	queryRoles: (body: QueryRolesRequestBody) => ['queryRoles', body] as const,
	queryGroups: (body: QueryGroupsRequestBody) => ['queryGroups', body] as const,
	adminClientConfig: () => ['adminClientConfig'] as const,
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
			// Blanket retry:false, matching getCurrentUser/getVariable above: a 403/404 here is
			// permanent (missing authorization / unknown process), and this app has a dedicated
			// forbidden-state UI, so a fast failure matters more than retrying transient 5xx.
			retry: false,
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

	queryMessageSubscriptions: (body: QueryMessageSubscriptionsRequestBody) =>
		queryOptions({
			queryKey: queryKeys.queryMessageSubscriptions(body),
			queryFn: async (): Promise<QueryMessageSubscriptionsResponseBody> => {
				const {response, error} = await request(endpoints.queryMessageSubscriptions(body));
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

	queryUsers: (body: QueryUsersRequestBody) =>
		queryOptions({
			queryKey: queryKeys.users(body),
			queryFn: async (): Promise<QueryUsersResponseBody> => {
				const {response, error} = await request(endpoints.queryUsers(body));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	getUser: (username: string) =>
		queryOptions({
			queryKey: queryKeys.user(username),
			queryFn: async (): Promise<User> => {
				const {response, error} = await request(endpoints.getUser({username}));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	queryAuditLogs: (body: QueryAuditLogsRequestBody) =>
		queryOptions({
			queryKey: queryKeys.queryAuditLogs(body),
			queryFn: async (): Promise<QueryAuditLogsResponseBody> => {
				const {response, error} = await request(endpoints.queryAuditLogs(body));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	queryMappingRules: (body: QueryMappingRulesRequestBody) =>
		queryOptions({
			queryKey: queryKeys.queryMappingRules(body),
			queryFn: async (): Promise<QueryMappingRulesResponseBody> => {
				const {response, error} = await request(endpoints.queryMappingRules(body));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	queryClusterVariables: (body: QueryClusterVariablesRequestBody) =>
		queryOptions({
			queryKey: queryKeys.queryClusterVariables(body),
			queryFn: async (): Promise<QueryClusterVariablesResponseBody> => {
				const {response, error} = await request(endpoints.queryClusterVariables(body));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),
	searchGlobalTaskListeners: (body: QueryGlobalTaskListenersRequestBody) =>
		queryOptions({
			queryKey: queryKeys.searchGlobalTaskListeners(body),
			queryFn: async (): Promise<QueryGlobalTaskListenersResponseBody> => {
				const {response, error} = await request(endpoints.searchGlobalTaskListeners(body));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	getClusterVariable: (variable: Pick<ClusterVariable, 'name' | 'scope' | 'tenantId'>) =>
		queryOptions({
			queryKey: queryKeys.getClusterVariable(variable),
			queryFn: async (): Promise<ClusterVariable> => {
				const {response, error} = await request(endpoints.getClusterVariable(variable));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	queryTenants: (body: QueryTenantsRequestBody) =>
		queryOptions({
			queryKey: queryKeys.queryTenants(body),
			queryFn: async (): Promise<QueryTenantsResponseBody> => {
				const {response, error} = await request(endpoints.queryTenants(body));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),
	getGlobalTaskListener: (id: string) =>
		queryOptions({
			queryKey: queryKeys.globalTaskListener(id),
			queryFn: async (): Promise<GlobalTaskListener> => {
				const {response, error} = await request(endpoints.getGlobalTaskListener({id}));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	queryAuthorizations: (body: QueryAuthorizationsRequestBody) =>
		queryOptions({
			queryKey: queryKeys.queryAuthorizations(body),
			queryFn: async (): Promise<QueryAuthorizationsResponseBody> => {
				const {response, error} = await request(endpoints.queryAuthorizations(body));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	getAuthorization: (authorization: Pick<Authorization, 'authorizationKey'>) =>
		queryOptions({
			queryKey: queryKeys.getAuthorization(authorization),
			queryFn: async (): Promise<Authorization> => {
				const {response, error} = await request(endpoints.getAuthorization(authorization));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	queryRoles: (body: QueryRolesRequestBody) =>
		queryOptions({
			queryKey: queryKeys.queryRoles(body),
			queryFn: async (): Promise<QueryRolesResponseBody> => {
				const {response, error} = await request(endpoints.queryRoles(body));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	queryGroups: (body: QueryGroupsRequestBody) =>
		queryOptions({
			queryKey: queryKeys.queryGroups(body),
			queryFn: async (): Promise<QueryGroupsResponseBody> => {
				const {response, error} = await request(endpoints.queryGroups(body));
				if (error !== null) {
					throw mapQueryError(error);
				}
				return response.json();
			},
		}),

	// Fixed for the lifetime of the server, so it is fetched once per session.
	adminClientConfig: () =>
		queryOptions({
			queryKey: queryKeys.adminClientConfig(),
			staleTime: Infinity,
			queryFn: async (): Promise<AdminClientConfig> => {
				const {response, error} = await request(endpoints.getAdminClientConfig());
				if (error !== null) {
					throw mapQueryError(error);
				}
				return parseAdminClientConfig(await response.text());
			},
		}),
} as const;

export {queries};
