/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions} from '@tanstack/react-query';
import type {
	QueryDecisionInstancesResponseBody,
	QueryElementInstanceInspectionResponseBody,
	QueryJobsResponseBody,
	QueryMessageSubscriptionsResponseBody,
	QueryProcessInstancesResponseBody,
	QueryUserTasksResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';

const POLLING_INTERVAL_MS = 5000;
const MAX_WAIT_STATES = 1000;

async function getJson<T>(endpoint: Request): Promise<T> {
	const {response, error} = await request(endpoint);
	if (error !== null) {
		throw mapQueryError(error);
	}
	return response.json();
}

function waitStatesQuery({
	processInstanceKey,
	elementInstanceKey,
}: {
	processInstanceKey: string;
	elementInstanceKey: string;
}) {
	return queryOptions({
		queryKey: ['elementInstanceWaitStates', processInstanceKey, elementInstanceKey] as const,
		queryFn: async () =>
			(
				await getJson<QueryElementInstanceInspectionResponseBody>(
					endpoints.queryElementInstanceInspection({
						filter: {processInstanceKey, elementInstanceKey},
						page: {limit: MAX_WAIT_STATES},
					}),
				)
			).items.filter((item) => item.elementInstanceKey === elementInstanceKey),
		refetchInterval: POLLING_INTERVAL_MS,
	});
}

function calledProcessInstancesQuery(elementInstanceKey: string) {
	return queryOptions({
		queryKey: ['detailsCalledProcessInstances', elementInstanceKey] as const,
		queryFn: () =>
			getJson<QueryProcessInstancesResponseBody>(
				endpoints.queryProcessInstances({filter: {parentElementInstanceKey: elementInstanceKey}, page: {limit: 1}}),
			),
	});
}

function elementJobQuery(elementInstanceKey: string) {
	return queryOptions({
		queryKey: ['detailsJob', elementInstanceKey] as const,
		queryFn: async () =>
			(
				await getJson<QueryJobsResponseBody>(
					endpoints.queryJobs({filter: {elementInstanceKey, listenerEventType: 'UNSPECIFIED'}, page: {limit: 1}}),
				)
			).items[0] ?? null,
	});
}

function calledDecisionInstanceQuery(elementInstanceKey: string) {
	return queryOptions({
		queryKey: ['detailsCalledDecisionInstance', elementInstanceKey] as const,
		queryFn: async () =>
			(
				await getJson<QueryDecisionInstancesResponseBody>(
					endpoints.queryDecisionInstances({filter: {elementInstanceKey}}),
				)
			).items.find((instance) => instance.rootDecisionDefinitionKey === instance.decisionDefinitionKey) ?? null,
	});
}

function elementUserTaskQuery(elementInstanceKey: string) {
	return queryOptions({
		queryKey: ['detailsUserTask', elementInstanceKey] as const,
		queryFn: async () =>
			(
				await getJson<QueryUserTasksResponseBody>(
					endpoints.queryUserTasks({filter: {elementInstanceKey}, page: {limit: 1}}),
				)
			).items[0] ?? null,
	});
}

function elementMessageSubscriptionQuery(elementInstanceKey: string) {
	return queryOptions({
		queryKey: ['detailsMessageSubscription', elementInstanceKey] as const,
		queryFn: async () =>
			(
				await getJson<QueryMessageSubscriptionsResponseBody>(
					endpoints.queryMessageSubscriptions({
						filter: {elementInstanceKey: {$eq: elementInstanceKey}},
						page: {limit: 1},
					}),
				)
			).items[0] ?? null,
	});
}

export {
	waitStatesQuery,
	calledProcessInstancesQuery,
	elementJobQuery,
	calledDecisionInstanceQuery,
	elementUserTaskQuery,
	elementMessageSubscriptionQuery,
};
