/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const OPERATION_TYPE_TRANSLATION_KEYS: Record<string, string> = {
  ASSIGN: 'taskDetailsHistoryOperationAssign',
  CANCEL: 'taskDetailsHistoryOperationCancel',
  COMPLETE: 'taskDetailsHistoryOperationComplete',
  CREATE: 'taskDetailsHistoryOperationCreate',
  DELETE: 'taskDetailsHistoryOperationDelete',
  EVALUATE: 'taskDetailsHistoryOperationEvaluate',
  MIGRATE: 'taskDetailsHistoryOperationMigrate',
  MODIFY: 'taskDetailsHistoryOperationModify',
  RESOLVE: 'taskDetailsHistoryOperationResolve',
  RESUME: 'taskDetailsHistoryOperationResume',
  SUSPEND: 'taskDetailsHistoryOperationSuspend',
  UNASSIGN: 'taskDetailsHistoryOperationUnassign',
  UPDATE: 'taskDetailsHistoryOperationUpdate',
};

function getOperationTypeTranslationKey(operationType: string): string {
  return OPERATION_TYPE_TRANSLATION_KEYS[operationType] ?? operationType;
}

export {getOperationTypeTranslationKey};
