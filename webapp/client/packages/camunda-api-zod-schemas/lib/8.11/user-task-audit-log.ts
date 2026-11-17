/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {auditLogSearchQueryResultSchema} from './gen/zod/auditLogSearchQueryResultSchema';
import {userTaskAuditLogFilterSchema as genUserTaskAuditLogFilterSchema} from './gen/zod/userTaskAuditLogFilterSchema';
import {userTaskAuditLogSearchQueryRequestSchema} from './gen/zod/userTaskAuditLogSearchQueryRequestSchema';
import type {AuditLogSearchQueryResult} from './gen/types/AuditLogSearchQueryResult';
import type {UserTaskAuditLogFilter as GenUserTaskAuditLogFilter} from './gen/types/UserTaskAuditLogFilter';
import type {UserTaskAuditLogSearchQueryRequest} from './gen/types/UserTaskAuditLogSearchQueryRequest';

const userTaskAuditLogFilterSchema = genUserTaskAuditLogFilterSchema;
type UserTaskAuditLogFilter = GenUserTaskAuditLogFilter;

const queryUserTaskAuditLogsRequestBodySchema = userTaskAuditLogSearchQueryRequestSchema;
type QueryUserTaskAuditLogsRequestBody = UserTaskAuditLogSearchQueryRequest;

const queryUserTaskAuditLogsResponseBodySchema = auditLogSearchQueryResultSchema;
type QueryUserTaskAuditLogsResponseBody = AuditLogSearchQueryResult;

const queryUserTaskAuditLogs = {
	method: 'POST',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/audit-logs/search` as const,
} as const satisfies Endpoint<{userTaskKey: string}>;

export {
	userTaskAuditLogFilterSchema,
	queryUserTaskAuditLogsRequestBodySchema,
	queryUserTaskAuditLogsResponseBodySchema,
	queryUserTaskAuditLogs,
};

export type {UserTaskAuditLogFilter, QueryUserTaskAuditLogsRequestBody, QueryUserTaskAuditLogsResponseBody};
