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

/**
 * Instances can advance while the migration is being mapped, so the summary enables this query when
 * it opens and only shows statistics once they are fresh; cached ones are hidden while refetching and
 * after a failed refetch.
 */
function useMigrationStatistics({
	processDefinitionKey,
	filter,
	enabled,
}: {
	processDefinitionKey: string;
	filter: GetProcessDefinitionStatisticsRequestBody['filter'];
	enabled: boolean;
}) {
	const {data, status, isFetching} = useQuery({
		queryKey: ['migrationStatistics', processDefinitionKey, filter],
		enabled,
		staleTime: 0,
		queryFn: async (): Promise<GetProcessDefinitionStatisticsResponseBody> => {
			const {response, error} = await request(endpoints.getProcessDefinitionStatistics({processDefinitionKey, filter}));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json();
		},
	});

	return status === 'success' && !isFetching ? data : undefined;
}

export {useMigrationStatistics};
