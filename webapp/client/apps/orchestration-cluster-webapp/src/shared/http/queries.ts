/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions, infiniteQueryOptions} from '@tanstack/react-query';
import type {
	GetSystemConfigurationResponseBody,
	CurrentUser,
	License,
	GetProcessDefinitionInstanceStatisticsRequestBody,
	GetProcessDefinitionInstanceStatisticsResponseBody,
	GetIncidentProcessInstanceStatisticsByErrorRequestBody,
	GetIncidentProcessInstanceStatisticsByErrorResponseBody,
	ProcessDefinitionInstanceStatistics,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from './request';
import {endpoints} from './endpoints';

const DASHBOARD_PAGE_LIMIT = 50;
const DASHBOARD_MAX_PAGES = 5;

type RunningInstancesCount = {
	total: number;
	withIncidents: number;
	withoutIncidents: number;
};

function aggregateRunningInstancesCount(items: ProcessDefinitionInstanceStatistics[]): RunningInstancesCount {
	const withIncidents = items.reduce((sum, s) => sum + s.activeInstancesWithIncidentCount, 0);
	const withoutIncidents = items.reduce((sum, s) => sum + s.activeInstancesWithoutIncidentCount, 0);
	return {withIncidents, withoutIncidents, total: withIncidents + withoutIncidents};
}

const queryKeys = {
	currentUser: () => ['getCurrentUser'] as const,
	systemConfiguration: () => ['systemConfiguration'] as const,
	license: () => ['license'] as const,
	runningInstancesCount: () => ['runningInstancesCount'] as const,
	processDefinitionStatisticsPaginated: (payload: Omit<GetProcessDefinitionInstanceStatisticsRequestBody, 'page'>) =>
		['processDefinitionStatisticsPaginated', payload] as const,
	incidentStatisticsByErrorPaginated: (payload: Omit<GetIncidentProcessInstanceStatisticsByErrorRequestBody, 'page'>) =>
		['incidentStatisticsByErrorPaginated', payload] as const,
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

	getRunningInstancesCount: () =>
		queryOptions({
			queryKey: queryKeys.runningInstancesCount(),
			refetchInterval: 5000,
			queryFn: async (): Promise<RunningInstancesCount> => {
				const defaultBody: GetProcessDefinitionInstanceStatisticsRequestBody = {
					sort: [{field: 'activeInstancesWithoutIncidentCount', order: 'desc'}],
				};
				const {response: firstResponse, error: firstError} = await request(
					endpoints.getProcessDefinitionInstanceStatistics(defaultBody),
				);
				if (firstError !== null) throw firstError;
				const first: GetProcessDefinitionInstanceStatisticsResponseBody = await firstResponse.json();

				if (first.page.totalItems <= first.items.length) {
					return aggregateRunningInstancesCount(first.items);
				}

				const {response: restResponse, error: restError} = await request(
					endpoints.getProcessDefinitionInstanceStatistics({
						...defaultBody,
						page: {from: first.items.length, limit: first.page.totalItems},
					}),
				);
				if (restError !== null) throw restError;
				const rest: GetProcessDefinitionInstanceStatisticsResponseBody = await restResponse.json();
				return aggregateRunningInstancesCount([...first.items, ...rest.items]);
			},
		}),

	getProcessDefinitionStatisticsPaginated: (payload: Omit<GetProcessDefinitionInstanceStatisticsRequestBody, 'page'>) =>
		infiniteQueryOptions({
			queryKey: queryKeys.processDefinitionStatisticsPaginated(payload),
			queryFn: async ({pageParam}): Promise<GetProcessDefinitionInstanceStatisticsResponseBody> => {
				const {response, error} = await request(
					endpoints.getProcessDefinitionInstanceStatistics({
						...payload,
						page: {from: pageParam, limit: DASHBOARD_PAGE_LIMIT},
					}),
				);
				if (error !== null) throw error;
				return response.json();
			},
			staleTime: 5000,
			maxPages: DASHBOARD_MAX_PAGES,
			initialPageParam: 0,
			getNextPageParam: (lastPage, _allPages, lastPageParam) => {
				const next = lastPageParam + DASHBOARD_PAGE_LIMIT;
				return next >= lastPage.page.totalItems ? null : next;
			},
			getPreviousPageParam: (_firstPage, _allPages, firstPageParam) => {
				const prev = firstPageParam - DASHBOARD_PAGE_LIMIT;
				return prev < 0 ? null : prev;
			},
		}),

	getIncidentStatisticsByErrorPaginated: (
		payload: Omit<GetIncidentProcessInstanceStatisticsByErrorRequestBody, 'page'>,
	) =>
		infiniteQueryOptions({
			queryKey: queryKeys.incidentStatisticsByErrorPaginated(payload),
			queryFn: async ({pageParam}): Promise<GetIncidentProcessInstanceStatisticsByErrorResponseBody> => {
				const {response, error} = await request(
					endpoints.getIncidentProcessInstanceStatisticsByError({
						...payload,
						page: {from: pageParam, limit: DASHBOARD_PAGE_LIMIT},
					}),
				);
				if (error !== null) throw error;
				return response.json();
			},
			staleTime: 5000,
			maxPages: DASHBOARD_MAX_PAGES,
			initialPageParam: 0,
			getNextPageParam: (lastPage, _allPages, lastPageParam) => {
				const next = lastPageParam + DASHBOARD_PAGE_LIMIT;
				return next >= lastPage.page.totalItems ? null : next;
			},
			getPreviousPageParam: (_firstPage, _allPages, firstPageParam) => {
				const prev = firstPageParam - DASHBOARD_PAGE_LIMIT;
				return prev < 0 ? null : prev;
			},
		}),
} as const;

export {queries};
