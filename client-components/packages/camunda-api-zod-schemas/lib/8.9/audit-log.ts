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
	getQueryResponseBodySchema,
	type Endpoint,
} from '../common';
import {batchOperationTypeSchema} from '../8.8';

const auditLogEntityTypeSchema = z.enum([
	'AUTHORIZATION',
	'BATCH',
	'DECISION',
	'GROUP',
	'INCIDENT',
	'MAPPING_RULE',
	'PROCESS_INSTANCE',
	'ROLE',
	'TENANT',
	'USER',
	'USER_TASK',
	'RESOURCE',
	'VARIABLE',
]);
type AuditLogEntityType = z.infer<typeof auditLogEntityTypeSchema>;

const auditLogOperationTypeSchema = z.enum([
	'ASSIGN',
	'CANCEL',
	'COMPLETE',
	'CREATE',
	'DELETE',
	'EVALUATE',
	'MIGRATE',
	'MODIFY',
	'RESOLVE',
	'RESUME',
	'SUSPEND',
	'UNASSIGN',
	'UPDATE',
]);
type AuditLogOperationType = z.infer<typeof auditLogOperationTypeSchema>;

const auditLogActorTypeSchema = z.enum(['USER', 'CLIENT', 'ANONYMOUS', 'UNKNOWN']);
type AuditLogActorType = z.infer<typeof auditLogActorTypeSchema>;

const auditLogResultSchema = z.enum(['SUCCESS', 'FAIL']);
type AuditLogResult = z.infer<typeof auditLogResultSchema>;

const auditLogCategorySchema = z.enum(['DEPLOYED_RESOURCES', 'USER_TASKS', 'ADMIN']);
type AuditLogCategory = z.infer<typeof auditLogCategorySchema>;

const auditLogSchema = z.object({
	auditLogKey: z.string(),
	entityKey: z.string(),
	entityType: auditLogEntityTypeSchema,
	operationType: auditLogOperationTypeSchema,
	batchOperationKey: z.string().optional(),
	batchOperationType: batchOperationTypeSchema.optional(),
	timestamp: z.string(),
	actorId: z.string(),
	actorType: auditLogActorTypeSchema,
	tenantId: z.string().optional(),
	result: auditLogResultSchema,
	annotation: z.string().optional(),
	category: auditLogCategorySchema,
	processDefinitionId: z.string().optional(),
	processDefinitionKey: z.string().optional(),
	processInstanceKey: z.string().optional(),
	elementInstanceKey: z.string().optional(),
	jobKey: z.string().optional(),
	userTaskKey: z.string().optional(),
	decisionRequirementsId: z.string().optional(),
	decisionRequirementsKey: z.string().optional(),
	decisionDefinitionId: z.string().optional(),
	decisionDefinitionKey: z.string().optional(),
	decisionEvaluationKey: z.string().optional(),
	deploymentKey: z.string().optional(),
	formKey: z.string().optional(),
	resourceKey: z.string().optional(),
	relatedEntityKey: z.string().optional(),
	relatedEntityType: auditLogEntityTypeSchema.optional(),
	entityDescription: z.string().optional(),
	agentElementId: z.string().optional(),
});
type AuditLog = z.infer<typeof auditLogSchema>;

const auditLogFilterSchema = z
	.object({
		auditLogKey: advancedStringFilterSchema.optional(),
		processDefinitionKey: advancedStringFilterSchema.optional(),
		processInstanceKey: advancedStringFilterSchema.optional(),
		elementInstanceKey: advancedStringFilterSchema.optional(),
		operationType: getEnumFilterSchema(auditLogOperationTypeSchema).optional(),
		result: getEnumFilterSchema(auditLogResultSchema).optional(),
		timestamp: advancedDateTimeFilterSchema.optional(),
		actorId: advancedStringFilterSchema.optional(),
		actorType: getEnumFilterSchema(auditLogActorTypeSchema).optional(),
		entityType: getEnumFilterSchema(auditLogEntityTypeSchema).optional(),
		tenantId: advancedStringFilterSchema.optional(),
		category: getEnumFilterSchema(auditLogCategorySchema).optional(),
		deploymentKey: advancedStringFilterSchema.optional(),
		formKey: advancedStringFilterSchema.optional(),
		resourceKey: advancedStringFilterSchema.optional(),
		relatedEntityType: getEnumFilterSchema(auditLogEntityTypeSchema).optional(),
		entityDescription: advancedStringFilterSchema.optional(),
	})
	.partial();

const auditLogSortFieldEnum = z.enum([
	'actorId',
	'actorType',
	'annotation',
	'auditLogKey',
	'batchOperationKey',
	'batchOperationType',
	'category',
	'decisionDefinitionId',
	'decisionDefinitionKey',
	'decisionEvaluationKey',
	'decisionRequirementsId',
	'decisionRequirementsKey',
	'elementInstanceKey',
	'entityKey',
	'entityType',
	'jobKey',
	'operationType',
	'processDefinitionId',
	'processDefinitionKey',
	'processInstanceKey',
	'result',
	'tenantId',
	'timestamp',
	'userTaskKey',
]);
type AuditLogSortField = z.infer<typeof auditLogSortFieldEnum>;

const queryAuditLogsRequestBodySchema = getQueryRequestBodySchema({
	sortFields: auditLogSortFieldEnum.options as [string, ...string[]],
	filter: auditLogFilterSchema,
});
type QueryAuditLogsRequestBody = z.infer<typeof queryAuditLogsRequestBodySchema>;

const queryAuditLogsResponseBodySchema = getQueryResponseBodySchema(auditLogSchema);
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
