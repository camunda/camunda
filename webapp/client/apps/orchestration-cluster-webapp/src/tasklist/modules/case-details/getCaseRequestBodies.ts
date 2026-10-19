/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
	QueryElementInstancesRequestBody,
	QueryUserTasksRequestBody,
	QueryVariablesRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';

const MAX_ITEMS = 1000;

function getCaseElementInstancesRequestBody(processInstanceKey: string): QueryElementInstancesRequestBody {
	return {
		filter: {processInstanceKey},
		sort: [{field: 'startDate', order: 'ASC'}],
		page: {limit: MAX_ITEMS},
	};
}

function getCaseUserTasksRequestBody(processInstanceKey: string): QueryUserTasksRequestBody {
	return {
		filter: {processInstanceKey},
		sort: [{field: 'creationDate', order: 'ASC'}],
		page: {limit: MAX_ITEMS},
	};
}

/** Case data lives in the root scope (CASE_DATA.md), so one search serves the title, header, documents and form. */
function getRootVariablesRequestBody(processInstanceKey: string): QueryVariablesRequestBody {
	return {
		filter: {processInstanceKey, scopeKey: processInstanceKey},
		page: {limit: MAX_ITEMS},
	};
}

export {getCaseElementInstancesRequestBody, getCaseUserTasksRequestBody, getRootVariablesRequestBody};
