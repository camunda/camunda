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
	auditLogResultSchema,
	auditLogActorTypeSchema,
	auditLogSortFieldEnum,
	queryAuditLogsResponseBodySchema,
} from './audit-log';

const userTaskAuditLogFilterSchema = z
	.object({
		operationType: getEnumFilterSchema(auditLogOperationTypeSchema).optional(),
		result: getEnumFilterSchema(auditLogResultSchema).optional(),
		timestamp: advancedDateTimeFilterSchema.optional(),
		actorType: getEnumFilterSchema(auditLogActorTypeSchema).optional(),
		actorId: advancedStringFilterSchema.optional(),
	})
	.partial();
type UserTaskAuditLogFilter = z.infer<typeof userTaskAuditLogFilterSchema>;

const queryUserTaskAuditLogsRequestBodySchema = getQueryRequestBodySchema({
	sortFields: auditLogSortFieldEnum.options as [string, ...string[]],
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
