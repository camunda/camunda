/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AuditLogOperationType} from '@camunda/camunda-api-zod-schemas/8.10';

const OPERATION_TYPE_TRANSLATION_KEY: Partial<Record<AuditLogOperationType, string>> = {
	ASSIGN: 'tasklist.taskDetailsHistoryOperationAssign',
	CANCEL: 'tasklist.taskDetailsHistoryOperationCancel',
	COMPLETE: 'tasklist.taskDetailsHistoryOperationComplete',
	CREATE: 'tasklist.taskDetailsHistoryOperationCreate',
	DELETE: 'tasklist.taskDetailsHistoryOperationDelete',
	EVALUATE: 'tasklist.taskDetailsHistoryOperationEvaluate',
	MIGRATE: 'tasklist.taskDetailsHistoryOperationMigrate',
	MODIFY: 'tasklist.taskDetailsHistoryOperationModify',
	RESOLVE: 'tasklist.taskDetailsHistoryOperationResolve',
	RESUME: 'tasklist.taskDetailsHistoryOperationResume',
	SUSPEND: 'tasklist.taskDetailsHistoryOperationSuspend',
	UNASSIGN: 'tasklist.taskDetailsHistoryOperationUnassign',
	UPDATE: 'tasklist.taskDetailsHistoryOperationUpdate',
};

function getOperationTypeTranslationKey(operationType: AuditLogOperationType) {
	return OPERATION_TYPE_TRANSLATION_KEY[operationType] ?? operationType;
}

export {getOperationTypeTranslationKey};
