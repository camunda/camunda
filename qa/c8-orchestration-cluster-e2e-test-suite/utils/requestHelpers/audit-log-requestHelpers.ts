/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export interface AuditLog {
  auditLogKey: string;
  entityKey: string;
  entityType:
    | 'AUTHORIZATION'
    | 'BATCH'
    | 'DECISION'
    | 'GROUP'
    | 'INCIDENT'
    | 'JOB'
    | 'MAPPING_RULE'
    | 'PROCESS_INSTANCE'
    | 'RESOURCE'
    | 'ROLE'
    | 'TENANT'
    | 'USER'
    | 'USER_TASK'
    | 'VARIABLE'
    | 'CLIENT';
  operationType:
    | 'ASSIGN'
    | 'CANCEL'
    | 'COMPLETE'
    | 'CREATE'
    | 'DELETE'
    | 'EVALUATE'
    | 'MIGRATE'
    | 'MODIFY'
    | 'RESOLVE'
    | 'RESUME'
    | 'SUSPEND'
    | 'UNASSIGN'
    | 'UNKNOWN'
    | 'UPDATE';
  batchOperationKey: string | null;
  batchOperationType:
    | 'ADD_VARIABLE'
    | 'CANCEL_PROCESS_INSTANCE'
    | 'DELETE_DECISION_DEFINITION'
    | 'DELETE_DECISION_INSTANCE'
    | 'DELETE_PROCESS_DEFINITION'
    | 'DELETE_PROCESS_INSTANCE'
    | 'MIGRATE_PROCESS_INSTANCE'
    | 'MODIFY_PROCESS_INSTANCE'
    | 'RESOLVE_INCIDENT'
    | 'UPDATE_VARIABLE'
    | null;
  timestamp: string;
  actorId: string | null;
  actorType: 'ANONYMOUS' | 'CLIENT' | 'UNKNOWN' | 'USER' | null;
  agentElementId: string | null;
  tenantId: string | null;
  result: 'SUCCESS' | 'FAIL';
  category: 'ADMIN' | 'DEPLOYED_RESOURCES' | 'USER_TASKS';
  processDefinitionId: string | null;
  processDefinitionKey: string | null;
  processInstanceKey: string | null;
  rootProcessInstanceKey: string | null;
  elementInstanceKey: string | null;
  jobKey: string | null;
  userTaskKey: string | null;
  decisionRequirementsId: string | null;
  decisionRequirementsKey: string | null;
  decisionDefinitionId: string | null;
  decisionDefinitionKey: string | null;
  decisionEvaluationKey: string | null;
  deploymentKey: string | null;
  formKey: string | null;
  resourceKey: string | null;
  relatedEntityKey: string | null;
  relatedEntityType:
    | 'AUTHORIZATION'
    | 'BATCH'
    | 'DECISION'
    | 'GROUP'
    | 'INCIDENT'
    | 'JOB'
    | 'MAPPING_RULE'
    | 'PROCESS_INSTANCE'
    | 'RESOURCE'
    | 'ROLE'
    | 'TENANT'
    | 'USER'
    | 'USER_TASK'
    | 'VARIABLE'
    | 'CLIENT'
    | null;
  entityDescription: string | null;
}
