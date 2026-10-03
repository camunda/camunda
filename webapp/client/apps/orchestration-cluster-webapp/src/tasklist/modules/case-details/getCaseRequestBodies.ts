/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
	ProcessInstance,
	QueryElementInstanceInspectionRequestBody,
	QueryElementInstancesRequestBody,
	QueryProcessInstanceIncidentsRequestBody,
	QueryUserTasksRequestBody,
	QueryVariablesRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {METADATA_VARIABLE_NAME} from '#/tasklist/modules/case-details/caseMetadataSchema';

const MAX_ITEMS = 1000;
const MAX_VARIABLES = 100;

function getMetadataVariableRequestBody(processInstanceKey: string): QueryVariablesRequestBody {
	return {
		filter: {processInstanceKey, scopeKey: processInstanceKey, name: METADATA_VARIABLE_NAME},
		page: {from: 0, limit: 1},
	};
}

function getFormVariablesRequestBody(processInstanceKey: string, names: string[]): QueryVariablesRequestBody {
	return {
		filter: {processInstanceKey, scopeKey: processInstanceKey, name: {$in: names}},
		page: {from: 0, limit: names.length},
	};
}

function getDocumentVariablesRequestBody(processInstanceKey: string): QueryVariablesRequestBody {
	return {
		filter: {processInstanceKey, scopeKey: processInstanceKey, value: {$like: '*camunda.document.type*'}},
		sort: [{field: 'name', order: 'asc'}],
		page: {from: 0, limit: MAX_VARIABLES},
	};
}

function getCaseElementInstancesRequestBody(processInstanceKey: string): QueryElementInstancesRequestBody {
	return {
		filter: {processInstanceKey},
		sort: [{field: 'startDate', order: 'asc'}],
		page: {from: 0, limit: MAX_ITEMS},
	};
}

function getCaseWaitStatesRequestBody(processInstanceKey: string): QueryElementInstanceInspectionRequestBody {
	return {
		filter: {rootProcessInstanceKey: {$eq: processInstanceKey}},
		page: {from: 0, limit: MAX_ITEMS},
	};
}

function getCaseUserTasksRequestBody({processInstanceKey, businessId}: ProcessInstance): QueryUserTasksRequestBody {
	return {
		// Tasks of called processes inherit the case business ID; without one, only root tasks are found.
		filter: businessId === null ? {processInstanceKey} : {businessId: {$eq: businessId}},
		sort: [{field: 'creationDate', order: 'desc'}],
		page: {from: 0, limit: MAX_ITEMS},
	};
}

function getCaseIncidentsRequestBody(): QueryProcessInstanceIncidentsRequestBody {
	return {
		sort: [{field: 'creationTime', order: 'desc'}],
		page: {from: 0, limit: MAX_ITEMS},
	};
}

export {
	getCaseElementInstancesRequestBody,
	getCaseIncidentsRequestBody,
	getCaseUserTasksRequestBody,
	getCaseWaitStatesRequestBody,
	getDocumentVariablesRequestBody,
	getFormVariablesRequestBody,
	getMetadataVariableRequestBody,
};
