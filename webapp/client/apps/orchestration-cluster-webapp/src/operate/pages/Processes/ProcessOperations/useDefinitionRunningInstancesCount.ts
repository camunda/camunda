/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import type {QueryProcessInstancesResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {request} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';
import {mapQueryError} from '#/shared/http/mapQueryError';

function useDefinitionRunningInstancesCount(processDefinitionKey: string) {
	return useQuery({
		queryKey: ['processInstances', 'runningInstancesCount', processDefinitionKey] as const,
		queryFn: async () => {
			const {response, error} = await request(
				endpoints.queryProcessInstances({
					filter: {
						processDefinitionKey: {$eq: processDefinitionKey},
						$or: [{state: {$eq: 'ACTIVE'}}, {hasIncident: true}],
					},
					page: {from: 0, limit: 0},
				}),
			);
			if (error !== null) {
				throw mapQueryError(error);
			}
			const result: QueryProcessInstancesResponseBody = await response.json();
			return result.page.totalItems;
		},
		refetchOnWindowFocus: true,
		placeholderData: (previousData) => previousData,
	});
}

export {useDefinitionRunningInstancesCount};
