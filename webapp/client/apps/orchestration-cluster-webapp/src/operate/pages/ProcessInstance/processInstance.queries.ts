/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions} from '@tanstack/react-query';
import type {
	ProcessInstance,
	QueryProcessInstanceIncidentsResponseBody,
	GetProcessInstanceWaitStateStatisticsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {endpoints} from '#/shared/http/endpoints';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {getClientConfig} from '#/shared/config/getClientConfig';
import {isInstanceRunning} from '#/operate/shared/utils/instance';

function processInstanceQuery(processInstanceKey: string) {
	return queryOptions({
		queryKey: ['processInstance', processInstanceKey] as const,
		queryFn: async (): Promise<ProcessInstance> => {
			const {response, error} = await request(endpoints.getProcessInstance({processInstanceKey}));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json();
		},
		staleTime: 500,
		refetchInterval: ({state: {data}}) => (data && isInstanceRunning(data) ? 5000 : false),
	});
}

function processInstanceIncidentsCountQuery(processInstanceKey: string) {
	return queryOptions({
		queryKey: ['processInstanceIncidentsCount', processInstanceKey] as const,
		queryFn: async () => {
			const {response, error} = await request(
				endpoints.queryProcessInstanceIncidents(
					{processInstanceKey},
					{
						filter: {state: 'ACTIVE'},
						page: {limit: 0},
					},
				),
			);
			if (error !== null) {
				throw mapQueryError(error);
			}
			const data: QueryProcessInstanceIncidentsResponseBody = await response.json();
			return data.page.totalItems;
		},
		staleTime: 5000,
		refetchInterval: 5000,
	});
}

function waitStateStatisticsQuery(instance: ProcessInstance) {
	const processInstanceKey = instance.processInstanceKey;
	const enabled = getClientConfig().deployment.isWaitStatesEnabled && isInstanceRunning(instance);
	return queryOptions({
		queryKey: ['processInstanceWaitStates', processInstanceKey],
		queryFn: async ({signal}): Promise<GetProcessInstanceWaitStateStatisticsResponseBody> => {
			const {response, error} = await request(
				new Request(endpoints.getProcessInstanceWaitStateStatistics({processInstanceKey}), {signal}),
			);
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json();
		},
		enabled,
		refetchInterval: enabled ? 5000 : false,
		select: (data) => (enabled ? data.items : []),
	});
}

export {processInstanceQuery, processInstanceIncidentsCountQuery, waitStateStatisticsQuery};
