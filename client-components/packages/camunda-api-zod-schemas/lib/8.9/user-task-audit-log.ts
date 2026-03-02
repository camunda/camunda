/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {
	API_VERSION,
	advancedDateTimeFilterSchema,
	advancedStringFilterSchema,
	getEnumFilterSchema,
	getQueryRequestBodySchema,
	type Endpoint,
} from './common';
import {
	auditLogOperationTypeSchema,
	auditLogResultStatusSchema,
	auditLogActorTypeSchema,
	queryAuditLogsResponseBodySchema,
} from './audit-log';
import {auditLogSearchQuerySortRequestSchema} from './gen';

const userTaskAuditLogFilterSchema = z
	.object({
		operationType: getEnumFilterSchema(auditLogOperationTypeSchema).optional(),
		result: getEnumFilterSchema(auditLogResultStatusSchema).optional(),
		timestamp: advancedDateTimeFilterSchema.optional(),
		actorType: getEnumFilterSchema(auditLogActorTypeSchema).optional(),
		actorId: advancedStringFilterSchema.optional(),
	})
	.partial();
type UserTaskAuditLogFilter = z.infer<typeof userTaskAuditLogFilterSchema>;

const queryUserTaskAuditLogsRequestBodySchema = getQueryRequestBodySchema({
	sortFields: auditLogSearchQuerySortRequestSchema.shape.field.options as [string, ...string[]],
	filter: userTaskAuditLogFilterSchema,
});
type QueryUserTaskAuditLogsRequestBody = z.infer<typeof queryUserTaskAuditLogsRequestBodySchema>;

const queryUserTaskAuditLogsResponseBodySchema = queryAuditLogsResponseBodySchema;
type QueryUserTaskAuditLogsResponseBody = z.infer<typeof queryUserTaskAuditLogsResponseBodySchema>;

const queryUserTaskAuditLogs: Endpoint<{userTaskKey: string}> = {
	method: 'POST',
	getUrl: ({userTaskKey}) => `/${API_VERSION}/user-tasks/${userTaskKey}/audit-logs/search`,
};

export {
	userTaskAuditLogFilterSchema,
	queryUserTaskAuditLogsRequestBodySchema,
	queryUserTaskAuditLogsResponseBodySchema,
	queryUserTaskAuditLogs,
};
export type {UserTaskAuditLogFilter, QueryUserTaskAuditLogsRequestBody, QueryUserTaskAuditLogsResponseBody};
