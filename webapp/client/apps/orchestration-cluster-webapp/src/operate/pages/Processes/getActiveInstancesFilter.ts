/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CreateCancellationBatchOperationRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';

type SelectionFilter = CreateCancellationBatchOperationRequestBody['filter'];

function canMatchActive(state: NonNullable<SelectionFilter['state']>) {
	return (
		(state.$eq === undefined || state.$eq === 'ACTIVE') &&
		state.$neq !== 'ACTIVE' &&
		(state.$in === undefined || state.$in.includes('ACTIVE')) &&
		state.$exists !== false
	);
}

/**
 * Only active instances can be moved or migrated, so the selection is narrowed to its active instances.
 * Top-level criteria AND with `$or`, so the top-level `state` intersects every branch. Returns
 * `null` when the selection's state criteria, top-level or in every `$or` branch, exclude active
 * instances, as the API has no way to express a filter that matches nothing.
 */
function getActiveInstancesFilter(filter: SelectionFilter): SelectionFilter | null {
	const canSelectionMatchActive =
		(filter.state === undefined || canMatchActive(filter.state)) &&
		(filter.$or === undefined ||
			filter.$or.some((branch) => branch.state === undefined || canMatchActive(branch.state)));
	if (!canSelectionMatchActive) {
		return null;
	}
	return {...filter, state: {$eq: 'ACTIVE'}};
}

export {getActiveInstancesFilter};
