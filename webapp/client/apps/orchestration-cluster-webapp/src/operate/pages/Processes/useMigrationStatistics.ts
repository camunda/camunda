/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import type {
	GetProcessDefinitionStatisticsRequestBody,
	GetProcessDefinitionStatisticsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';

function useMigrationStatistics({
	processDefinitionKey,
	filter,
	enabled,
}: {
	processDefinitionKey: string;
	filter: GetProcessDefinitionStatisticsRequestBody['filter'];
	enabled: boolean;
}) {
	return useQuery({
		queryKey: ['processDefinitionStatistics', processDefinitionKey, filter],
		enabled,
		queryFn: async (): Promise<GetProcessDefinitionStatisticsResponseBody> => {
			const {response, error} = await request(endpoints.getProcessDefinitionStatistics({processDefinitionKey, filter}));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json();
		},
	});
}

export {useMigrationStatistics};
