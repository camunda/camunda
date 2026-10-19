/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
	ProcessInstance,
	QueryElementInstancesRequestBody,
	QueryUserTasksRequestBody,
	QueryVariablesRequestBody,
	UserTask,
} from '@camunda/camunda-api-zod-schemas/8.11';

const MAX_ITEMS = 1000;

function getCaseElementInstancesRequestBody(processInstanceKey: string): QueryElementInstancesRequestBody {
	return {
		filter: {processInstanceKey},
		sort: [{field: 'startDate', order: 'ASC'}],
		page: {limit: MAX_ITEMS},
	};
}

type CaseRef = Pick<ProcessInstance, 'processInstanceKey' | 'businessId'>;

// Tasks of called processes inherit the business ID of the case. The user task search has no root process
// instance filter, so the case page keeps the tasks whose root is the case (see isTaskOfCase).
function getCaseTaskFilter({processInstanceKey, businessId}: CaseRef): QueryUserTasksRequestBody['filter'] {
	return businessId === null || businessId === undefined ? {processInstanceKey} : {businessId: {$eq: businessId}};
}

function isTaskOfCase({processInstanceKey, rootProcessInstanceKey}: UserTask, caseKey: string): boolean {
	return (rootProcessInstanceKey ?? processInstanceKey) === caseKey;
}

function getCaseUserTasksRequestBody(caseRef: CaseRef): QueryUserTasksRequestBody {
	return {
		filter: getCaseTaskFilter(caseRef),
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

export {getCaseElementInstancesRequestBody, getCaseUserTasksRequestBody, getRootVariablesRequestBody, isTaskOfCase};
