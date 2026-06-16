/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions, useSuspenseQuery} from '@tanstack/react-query';
import type {
	GetProcessDefinitionInstanceStatisticsRequestBody,
	GetProcessDefinitionInstanceStatisticsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';

type RunningInstancesCount = {
	total: number;
	withIncidents: number;
	withoutIncidents: number;
};

const DEFAULT_SORT: GetProcessDefinitionInstanceStatisticsRequestBody = {
	sort: [{field: 'activeInstancesWithoutIncidentCount', order: 'desc'}],
};

function aggregateRunningInstancesCount(
	items: GetProcessDefinitionInstanceStatisticsResponseBody['items'],
): RunningInstancesCount {
	let withIncidents = 0;
	let withoutIncidents = 0;
	for (const item of items) {
		withIncidents += item.activeInstancesWithIncidentCount;
		withoutIncidents += item.activeInstancesWithoutIncidentCount;
	}
	return {withIncidents, withoutIncidents, total: withIncidents + withoutIncidents};
}

const runningInstancesCountQuery = () =>
	queryOptions({
		queryKey: ['runningInstancesCount'] as const,
		queryFn: async (): Promise<RunningInstancesCount> => {
			const {response: first, error: firstError} = await request(
				endpoints.getProcessDefinitionInstanceStatistics(DEFAULT_SORT),
			);
			if (firstError !== null) {
				throw firstError;
			}
			const firstPage: GetProcessDefinitionInstanceStatisticsResponseBody = await first.json();

			if (firstPage.page.totalItems <= firstPage.items.length) {
				return aggregateRunningInstancesCount(firstPage.items);
			}

			const {response: remaining, error: remainingError} = await request(
				endpoints.getProcessDefinitionInstanceStatistics({
					...DEFAULT_SORT,
					page: {from: firstPage.items.length, limit: firstPage.page.totalItems},
				}),
			);
			if (remainingError !== null) {
				throw remainingError;
			}
			const remainingPage: GetProcessDefinitionInstanceStatisticsResponseBody = await remaining.json();
			return aggregateRunningInstancesCount(firstPage.items.concat(remainingPage.items));
		},
	});

function useRunningInstancesCount() {
	return useSuspenseQuery({
		...runningInstancesCountQuery(),
		refetchInterval: 5000,
	});
}

export {useRunningInstancesCount, runningInstancesCountQuery};
