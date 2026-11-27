/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
	AuditLogEntityType,
	QueryAuditLogsRequestBody,
	QueryElementInstanceEffectiveVariablesRequestBody,
	QueryElementInstancesRequestBody,
	QueryUserTasksRequestBody,
	QueryVariablesRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.11';

const MAX_ITEMS = 1000;
const MAX_AUDIT_LOG_ITEMS = 100;
const CASE_AUDIT_LOG_ENTITY_TYPES = [
	'PROCESS_INSTANCE',
	'USER_TASK',
	'VARIABLE',
	'INCIDENT',
	'JOB',
] as const satisfies AuditLogEntityType[];

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
 * Reads case data from the root scope, for a case without a running stage. The search requests the
 * named variables only: the names come from the BPMN and the case form, never all variables of the case.
 */
function getCaseVariablesRequestBody(processInstanceKey: string, names: string[]): QueryVariablesRequestBody {
	return {
		filter: {processInstanceKey, scopeKey: processInstanceKey, name: {$in: [...names].sort()}},
		page: {limit: MAX_ITEMS},
	};
}

/**
 * Reads case data as the running stage sees it: an input mapping of the stage can shadow a root
 * variable, such as caseHeader, and the tasks of the stage then change the stage-local copy.
 */
function getStageCaseVariablesRequestBody(names: string[]): QueryElementInstanceEffectiveVariablesRequestBody {
	return {
		filter: {name: {$in: [...names].sort()}},
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

/** The latest audit log entries of a case. Only the entity types whose operations a case worker relates to. */
function getCaseAuditLogsRequestBody(processInstanceKey: string): QueryAuditLogsRequestBody {
	return {
		filter: {
			processInstanceKey,
			result: 'SUCCESS',
			entityType: {$in: [...CASE_AUDIT_LOG_ENTITY_TYPES]},
		},
		sort: [
			{field: 'timestamp', order: 'DESC'},
			{field: 'auditLogKey', order: 'DESC'},
		],
		page: {limit: MAX_AUDIT_LOG_ITEMS},
	};
}

export {
	getCaseAuditLogsRequestBody,
	getCaseElementInstancesRequestBody,
	getCaseUserTasksRequestBody,
	getCaseVariablesRequestBody,
	getDocumentVariablesRequestBody,
	getStageCaseVariablesRequestBody,
};
