/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions, useQuery} from '@tanstack/react-query';
import type {
	GetProcessInstanceWaitStateStatisticsResponseBody,
	ProcessInstance,
	QueryProcessInstanceIncidentsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {request, requestErrorSchema} from '#/shared/http/request';
import {ForbiddenError} from '#/shared/errors';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';
import {isInstanceRunning, shouldPollProcessInstance} from '#/operate/shared/utils/processInstance';
import {getClientConfig} from '#/shared/config/getClientConfig';

const POLLING_INTERVAL_MS = 5000;

function processInstanceQuery(processInstanceKey: string) {
	return queryOptions({
		queryKey: ['processInstance', processInstanceKey] as const,
		queryFn: async (): Promise<ProcessInstance> => {
			const {response, error} = await request(endpoints.getProcessInstance(processInstanceKey));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json();
		},
		staleTime: 500,
		refetchInterval: ({state: {data}}) => (data && shouldPollProcessInstance(data) ? POLLING_INTERVAL_MS : false),
	});
}

function processInstanceIncidentsCountQuery(processInstanceKey: string) {
	return queryOptions({
		queryKey: ['processInstanceIncidentsCount', processInstanceKey] as const,
		queryFn: async (): Promise<number> => {
			const {response, error} = await request(
				endpoints.queryProcessInstanceIncidents(processInstanceKey, {filter: {state: 'ACTIVE'}, page: {limit: 0}}),
			);
			if (error !== null) {
				throw mapQueryError(error);
			}
			const result: QueryProcessInstanceIncidentsResponseBody = await response.json();
			return result.page.totalItems;
		},
		staleTime: 500,
		refetchInterval: POLLING_INTERVAL_MS,
	});
}

function processInstanceWaitStateStatisticsQuery(processInstanceKey: string) {
	return queryOptions({
		queryKey: ['processInstanceWaitStateStatistics', processInstanceKey] as const,
		queryFn: async () => {
			const {response, error} = await request(endpoints.getProcessInstanceWaitStateStatistics(processInstanceKey));
			if (error !== null) {
				throw mapQueryError(error);
			}
			const result: GetProcessInstanceWaitStateStatisticsResponseBody = await response.json();
			return result.items;
		},
		staleTime: 500,
		refetchInterval: POLLING_INTERVAL_MS,
	});
}

function useProcessInstance(processInstanceKey: string) {
	const query = useQuery(processInstanceQuery(processInstanceKey));
	const requestError = requestErrorSchema.safeParse(query.error);
	const isUnauthorized = query.error instanceof ForbiddenError;
	const isNotFound = requestError.success && requestError.data.response?.status === 404;

	return {
		query,
		isUnauthorized,
		isNotFound,
		isGenericError: query.isError && !isUnauthorized && !isNotFound,
	};
}

function useProcessInstanceWaitStateStatistics(processInstance: ProcessInstance) {
	const enabled = getClientConfig().deployment.waitStatesEnabled && isInstanceRunning(processInstance);
	return useQuery({
		...processInstanceWaitStateStatisticsQuery(processInstance.processInstanceKey),
		enabled,
		select: (statistics) => (enabled ? statistics : []),
	});
}

export {
	processInstanceQuery,
	processInstanceIncidentsCountQuery,
	processInstanceWaitStateStatisticsQuery,
	useProcessInstance,
	useProcessInstanceWaitStateStatistics,
};
