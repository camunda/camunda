/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions, useQuery} from '@tanstack/react-query';
import type {ProcessInstance, GetProcessInstanceCallHierarchyResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {endpoints} from '#/shared/http/endpoints';

function callHierarchyOptions(processInstanceKey: ProcessInstance['processInstanceKey']) {
	return queryOptions({
		queryKey: ['processInstanceCallHierarchy', processInstanceKey] as const,
		queryFn: async (): Promise<GetProcessInstanceCallHierarchyResponseBody> => {
			const {response, error} = await request(endpoints.getProcessInstanceCallHierarchy({processInstanceKey}));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json();
		},
	});
}

function useCallHierarchy(processInstanceKey: ProcessInstance['processInstanceKey'], {enabled}: {enabled: boolean}) {
	return useQuery({
		...callHierarchyOptions(processInstanceKey),
		enabled,
	});
}

export {useCallHierarchy};
