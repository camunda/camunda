/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryElementInstanceInspectionRequestBody} from '@camunda/camunda-api-zod-schemas/8.11';

const MAX_WAIT_STATES = 1000;

function getWaitStatesRequestBody(rootProcessInstanceKeys: string[]): QueryElementInstanceInspectionRequestBody {
	return {
		filter: {rootProcessInstanceKey: {$in: rootProcessInstanceKeys}},
		sort: [{field: 'rootProcessInstanceKey', order: 'asc'}],
		page: {
			from: 0,
			// An empty $in is ignored by the API and would match every wait state.
			limit: rootProcessInstanceKeys.length === 0 ? 0 : MAX_WAIT_STATES,
		},
	};
}

export {getWaitStatesRequestBody};
