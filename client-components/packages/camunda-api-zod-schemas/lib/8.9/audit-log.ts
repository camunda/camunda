/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {
	auditLogEntityTypeEnumSchema,
	auditLogOperationTypeEnumSchema,
	auditLogActorTypeEnumSchema,
	auditLogResultEnumSchema,
	auditLogCategoryEnumSchema,
	auditLogResultSchema,
	auditLogFilterSchema as genAuditLogFilterSchema,
	auditLogSearchQueryRequestSchema,
	auditLogSearchQueryResultSchema,
} from './gen';

const auditLogEntityTypeSchema = auditLogEntityTypeEnumSchema;
type AuditLogEntityType = z.infer<typeof auditLogEntityTypeSchema>;

const auditLogOperationTypeSchema = auditLogOperationTypeEnumSchema;
type AuditLogOperationType = z.infer<typeof auditLogOperationTypeSchema>;

const auditLogActorTypeSchema = auditLogActorTypeEnumSchema;
type AuditLogActorType = z.infer<typeof auditLogActorTypeSchema>;

const auditLogResultStatusSchema = auditLogResultEnumSchema;
type AuditLogResultStatus = z.infer<typeof auditLogResultStatusSchema>;

const auditLogCategorySchema = auditLogCategoryEnumSchema;
type AuditLogCategory = z.infer<typeof auditLogCategorySchema>;

const auditLogSchema = auditLogResultSchema;
type AuditLog = z.infer<typeof auditLogSchema>;

const auditLogFilterSchema = genAuditLogFilterSchema;

const queryAuditLogsRequestBodySchema = auditLogSearchQueryRequestSchema;
type QueryAuditLogsRequestBody = z.infer<typeof queryAuditLogsRequestBodySchema>;

const queryAuditLogsResponseBodySchema = auditLogSearchQueryResultSchema;
type QueryAuditLogsResponseBody = z.infer<typeof queryAuditLogsResponseBodySchema>;

const queryAuditLogs: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/audit-logs/search`,
};

const getAuditLog: Endpoint<{auditLogKey: string}> = {
	method: 'GET',
	getUrl: ({auditLogKey}) => `/${API_VERSION}/audit-logs/${auditLogKey}`,
};

const getAuditLogResponseBodySchema = auditLogSchema;
type GetAuditLogResponseBody = z.infer<typeof getAuditLogResponseBodySchema>;

export {
	auditLogEntityTypeSchema,
	auditLogOperationTypeSchema,
	auditLogActorTypeSchema,
	auditLogResultStatusSchema,
	auditLogCategorySchema,
	auditLogSchema,
	auditLogFilterSchema,
	queryAuditLogsRequestBodySchema,
	queryAuditLogsResponseBodySchema,
	getAuditLogResponseBodySchema,
	queryAuditLogs,
	getAuditLog,
};
export type {
	AuditLog,
	AuditLogEntityType,
	AuditLogOperationType,
	AuditLogActorType,
	AuditLogResultStatus,
	AuditLogCategory,
	QueryAuditLogsRequestBody,
	QueryAuditLogsResponseBody,
	GetAuditLogResponseBody,
};
