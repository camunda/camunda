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

/**
 * Case data lives in the root scope (CASE_DATA.md). The search requests the named variables only: the
 * names come from the BPMN and the case form, never all variables of the case.
 */
function getCaseVariablesRequestBody(processInstanceKey: string, names: string[]): QueryVariablesRequestBody {
	return {
		filter: {processInstanceKey, scopeKey: processInstanceKey, name: {$in: [...names].sort()}},
		page: {limit: MAX_ITEMS},
	};
}

/** The BPMN does not name the document variables, so the search matches the document reference in the value. */
function getDocumentVariablesRequestBody(processInstanceKey: string): QueryVariablesRequestBody {
	return {
		filter: {processInstanceKey, scopeKey: processInstanceKey, value: {$like: '*camunda.document.type*'}},
		page: {limit: MAX_ITEMS},
	};
}

export {
	getCaseElementInstancesRequestBody,
	getCaseUserTasksRequestBody,
	getCaseVariablesRequestBody,
	getDocumentVariablesRequestBody,
};
