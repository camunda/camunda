/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CreateCancellationBatchOperationRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';

type SelectionFilter = CreateCancellationBatchOperationRequestBody['filter'];

/**
 * The batch cancel, suspend and resume endpoints reject a top-level `state` `$in` that contains states they
 * cannot act on. Narrows such a `$in` to the allowed states. Anything else, including `$or` branches, is
 * returned unchanged, as is a `$in` without any allowed state, which the API rejects with a clear message.
 */
function narrowTopLevelState(filter: SelectionFilter, allowedStates: readonly string[]): SelectionFilter {
	const state = filter.state;
	if (state === undefined || state.$in === undefined || Object.keys(state).some((key) => key !== '$in')) {
		return filter;
	}
	const kept = state.$in.filter((value) => allowedStates.includes(value));
	if (kept.length === 0 || kept.length === state.$in.length) {
		return filter;
	}
	return {...filter, state: kept.length === 1 ? {$eq: kept[0]} : {$in: kept}};
}

export {narrowTopLevelState};
