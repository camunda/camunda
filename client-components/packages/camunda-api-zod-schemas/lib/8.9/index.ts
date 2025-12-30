/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getAuditLog, queryAuditLogs} from './audit-log';
import {deleteProcessInstance} from './process-instance';

const endpoints = {
	queryAuditLogs,
	getAuditLog,
  deleteProcessInstance,
} as const;

export {endpoints};

export {
	auditLogEntityTypeSchema,
	auditLogOperationTypeSchema,
	auditLogActorTypeSchema,
	auditLogResultSchema,
	auditLogCategorySchema,
	auditLogSchema,
	auditLogFilterSchema,
	auditLogSortFieldEnum,
	queryAuditLogsRequestBodySchema,
	queryAuditLogsResponseBodySchema,
	getAuditLogResponseBodySchema,
	type AuditLog,
	type AuditLogEntityType,
	type AuditLogOperationType,
	type AuditLogActorType,
	type AuditLogResult,
	type AuditLogCategory,
	type AuditLogSortField,
	type QueryAuditLogsRequestBody,
	type QueryAuditLogsResponseBody,
	type GetAuditLogResponseBody,
} from './audit-log';

export {
  deleteProcessInstanceRequestBodySchema,
  type DeleteProcessInstanceRequestBody
} from './process-instance';
