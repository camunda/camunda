/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {auditLogActorTypeEnumSchema} from './gen/zod/auditLogActorTypeEnumSchema';
import {auditLogCategoryEnumSchema} from './gen/zod/auditLogCategoryEnumSchema';
import {auditLogEntityTypeEnumSchema} from './gen/zod/auditLogEntityTypeEnumSchema';
import {auditLogFilterSchema as genAuditLogFilterSchema} from './gen/zod/auditLogFilterSchema';
import {auditLogOperationTypeEnumSchema} from './gen/zod/auditLogOperationTypeEnumSchema';
import {auditLogResultEnumSchema} from './gen/zod/auditLogResultEnumSchema';
import {auditLogResultSchema as auditLogRecordSchema} from './gen/zod/auditLogResultSchema';
import {auditLogSearchQueryRequestSchema} from './gen/zod/auditLogSearchQueryRequestSchema';
import {auditLogSearchQueryResultSchema} from './gen/zod/auditLogSearchQueryResultSchema';
import {auditLogSearchQuerySortRequestSchema} from './gen/zod/auditLogSearchQuerySortRequestSchema';
import {getAuditLogStatus200Schema} from './gen/zod/getAuditLogSchema';
import type {AuditLogActorTypeEnumKey} from './gen/types/AuditLogActorTypeEnum';
import type {AuditLogCategoryEnumKey} from './gen/types/AuditLogCategoryEnum';
import type {AuditLogEntityTypeEnumKey} from './gen/types/AuditLogEntityTypeEnum';
import type {AuditLogOperationTypeEnumKey} from './gen/types/AuditLogOperationTypeEnum';
import type {AuditLogResultEnumKey} from './gen/types/AuditLogResultEnum';
import type {AuditLogResult as AuditLogRecord} from './gen/types/AuditLogResult';
import type {AuditLogSearchQueryRequest} from './gen/types/AuditLogSearchQueryRequest';
import type {AuditLogSearchQueryResult} from './gen/types/AuditLogSearchQueryResult';
import type {AuditLogSearchQuerySortRequestFieldEnumKey} from './gen/types/AuditLogSearchQuerySortRequest';
import type {GetAuditLogStatus200} from './gen/types/GetAuditLog';

const auditLogEntityTypeSchema = auditLogEntityTypeEnumSchema;
type AuditLogEntityType = AuditLogEntityTypeEnumKey;

const auditLogOperationTypeSchema = auditLogOperationTypeEnumSchema;
type AuditLogOperationType = AuditLogOperationTypeEnumKey;

const auditLogActorTypeSchema = auditLogActorTypeEnumSchema;
type AuditLogActorType = AuditLogActorTypeEnumKey;

// In gen, `auditLogResultSchema` / `AuditLogResult` is the audit log record. Here the names keep their meaning: the result enum.
const auditLogResultSchema = auditLogResultEnumSchema;
type AuditLogResult = AuditLogResultEnumKey;

const auditLogCategorySchema = auditLogCategoryEnumSchema;
type AuditLogCategory = AuditLogCategoryEnumKey;

const auditLogSchema = auditLogRecordSchema;
type AuditLog = AuditLogRecord;

const auditLogFilterSchema = genAuditLogFilterSchema;

const auditLogSortFieldEnum = auditLogSearchQuerySortRequestSchema.shape.field;
type AuditLogSortField = AuditLogSearchQuerySortRequestFieldEnumKey;

const queryAuditLogsRequestBodySchema = auditLogSearchQueryRequestSchema;
type QueryAuditLogsRequestBody = AuditLogSearchQueryRequest;

const queryAuditLogsResponseBodySchema = auditLogSearchQueryResultSchema;
type QueryAuditLogsResponseBody = AuditLogSearchQueryResult;

const getAuditLogResponseBodySchema = getAuditLogStatus200Schema;
type GetAuditLogResponseBody = GetAuditLogStatus200;

const queryAuditLogs = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/audit-logs/search` as const,
} as const satisfies Endpoint;

const getAuditLog = {
	method: 'GET',
	getUrl: ({auditLogKey}) => `/${API_VERSION}/audit-logs/${auditLogKey}` as const,
} as const satisfies Endpoint<{auditLogKey: string}>;

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
	queryAuditLogs,
	getAuditLog,
};

export type {
	AuditLog,
	AuditLogEntityType,
	AuditLogOperationType,
	AuditLogActorType,
	AuditLogResult,
	AuditLogCategory,
	AuditLogSortField,
	QueryAuditLogsRequestBody,
	QueryAuditLogsResponseBody,
	GetAuditLogResponseBody,
};
