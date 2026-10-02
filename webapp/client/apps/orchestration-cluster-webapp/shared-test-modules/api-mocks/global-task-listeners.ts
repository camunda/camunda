/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {GlobalTaskListener, QueryGlobalTaskListenersResponseBody} from '@camunda/camunda-api-zod-schemas/8.11';

function createGlobalTaskListener(overrides?: Partial<GlobalTaskListener>): GlobalTaskListener {
	return {
		id: 'my-global-task-listener',
		type: 'my-listener-type',
		eventTypes: ['creating'],
		retries: 3,
		afterNonGlobal: false,
		priority: 50,
		...overrides,
	};
}

function createQueryGlobalTaskListenersResponse(overrides?: {
	items?: GlobalTaskListener[];
	page?: Partial<QueryGlobalTaskListenersResponseBody['page']>;
}): QueryGlobalTaskListenersResponseBody {
	const items = overrides?.items ?? [];
	return {
		items,
		page: {
			totalItems: items.length,
			startCursor: null,
			endCursor: null,
			hasMoreTotalItems: false,
			...overrides?.page,
		},
	};
}

export {createGlobalTaskListener, createQueryGlobalTaskListenersResponse};
