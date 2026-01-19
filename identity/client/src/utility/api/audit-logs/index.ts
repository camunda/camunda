/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ApiDefinition, apiPost } from "src/utility/api/request";
import { SearchResponse } from "src/utility/api";
import { PageSearchParams } from "../hooks/usePagination";

export const AUDIT_LOGS_ENDPOINT = "/audit-logs";

export enum AuditLogEntityType {
  AUTHORIZATION = "AUTHORIZATION",
  BATCH = "BATCH",
  DECISION = "DECISION",
  GROUP = "GROUP",
  INCIDENT = "INCIDENT",
  MAPPING_RULE = "MAPPING_RULE",
  PROCESS_INSTANCE = "PROCESS_INSTANCE",
  RESOURCE = "RESOURCE",
  ROLE = "ROLE",
  TENANT = "TENANT",
  USER = "USER",
  USER_TASK = "USER_TASK",
  VARIABLE = "VARIABLE",
}

export enum AuditLogOperationType {
  ASSIGN = "ASSIGN",
  CANCEL = "CANCEL",
  COMPLETE = "COMPLETE",
  CREATE = "CREATE",
  DELETE = "DELETE",
  EVALUATE = "EVALUATE",
  MIGRATE = "MIGRATE",
  MODIFY = "MODIFY",
  RESOLVE = "RESOLVE",
  RESUME = "RESUME",
  SUSPEND = "SUSPEND",
  UNASSIGN = "UNASSIGN",
  UNKNOWN = "UNKNOWN",
  UPDATE = "UPDATE",
}

export enum AuditLogActorType {
  ANONYMOUS = "ANONYMOUS",
  CLIENT = "CLIENT",
  UNKNOWN = "UNKNOWN",
  USER = "USER",
}

export enum AuditLogResult {
  FAIL = "FAIL",
  SUCCESS = "SUCCESS",
}

export enum AuditLogCategory {
  ADMIN = "ADMIN",
  DEPLOYED_RESOURCES = "DEPLOYED_RESOURCES",
  USER_TASKS = "USER_TASKS",
}

export type AuditLog = {
  auditLogKey: string;
  entityKey: string;
  entityType: AuditLogEntityType;
  operationType: AuditLogOperationType;
  batchOperationKey?: string;
  batchOperationType?: string;
  timestamp: string;
  actorId: string;
  actorType: AuditLogActorType;
  tenantId?: string;
  result: AuditLogResult;
  annotation?: string;
  category: AuditLogCategory;
  processDefinitionId?: string;
  processDefinitionKey?: string;
  processInstanceKey?: string;
  elementInstanceKey?: string;
  jobKey?: string;
  userTaskKey?: string;
  decisionRequirementsId?: string;
  decisionRequirementsKey?: string;
  decisionDefinitionId?: string;
  decisionDefinitionKey?: string;
  decisionEvaluationKey?: string;
  deploymentKey?: string;
  formKey?: string;
  resourceKey?: string;
};

export type AuditLogFilter = {
  auditLogKey?: string;
  processDefinitionKey?: string;
  processInstanceKey?: string;
  elementInstanceKey?: string;
  operationType?: AuditLogOperationType;
  result?: AuditLogResult;
  timestamp?: string;
  actorId?: string;
  actorType?: AuditLogActorType;
  entityKey?: string;
  entityType?: AuditLogEntityType;
  tenantId?: string;
  category?: AuditLogCategory;
  deploymentKey?: string;
  formKey?: string;
  resourceKey?: string;
};

export type SearchAuditLogsParams = PageSearchParams & {
  filter?: AuditLogFilter;
};

export const searchAuditLogs: ApiDefinition<
  SearchResponse<AuditLog>,
  SearchAuditLogsParams | undefined
> = (params) => apiPost(`${AUDIT_LOGS_ENDPOINT}/search`, params);
