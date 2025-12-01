/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getAuditLog, searchAuditLogs} from './audit-log';

const endpoints = {
  searchAuditLogs,
  getAuditLog,
} as const;

export {endpoints};

export {
  auditLogEntityTypeSchema,
  auditLogOperationTypeSchema,
  auditLogActorTypeSchema,
  auditLogResultSchema,
  auditLogCategorySchema,
  auditLogSearchRequestBodySchema,
  auditLogSearchResponseBodySchema,
  auditLogSchema,
  auditLogFilterSchema,
  getAuditLogResponseBodySchema,
  type AuditLog,
  type SearchAuditLogsRequestBody,
  type SearchAuditLogsResponseBody,
  type GetAuditLogResponseBody,
} from './audit-log';
