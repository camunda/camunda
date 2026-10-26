/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {infiniteQueryOptions} from '@tanstack/react-query';
import type {QueryJobsResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {instanceRequest} from './processInstance.queries';

const LISTENERS_PAGE_SIZE = 50;

type ListenerKind = 'EXECUTION_LISTENER' | 'TASK_LISTENER';

function listenersQuery({
	processInstanceKey,
	elementId,
	elementInstanceKey,
	kind,
}: {
	processInstanceKey: string;
	elementId: string | null;
	elementInstanceKey: string | null;
	kind?: ListenerKind;
}) {
	return infiniteQueryOptions({
		queryKey: ['processInstanceListeners', processInstanceKey, elementId, elementInstanceKey, kind] as const,
		queryFn: ({pageParam, signal}) =>
			instanceRequest<QueryJobsResponseBody>(
				endpoints.queryJobs({
					filter: {
						processInstanceKey,
						elementId: elementId ?? undefined,
						elementInstanceKey: elementInstanceKey ?? undefined,
						kind: kind ?? {$in: ['EXECUTION_LISTENER', 'TASK_LISTENER']},
					},
					page: {limit: LISTENERS_PAGE_SIZE, from: pageParam},
				}),
				signal,
			),
		initialPageParam: 0,
		getNextPageParam: ({items, page}, _, lastPageParam) => {
			const nextPage = lastPageParam + LISTENERS_PAGE_SIZE;
			if (items.length < LISTENERS_PAGE_SIZE) {
				return undefined;
			}
			return page.hasMoreTotalItems || nextPage < page.totalItems ? nextPage : undefined;
		},
	});
}

export {listenersQuery};
export type {ListenerKind};
