/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getAuditLog, queryAuditLogs} from './audit-log';
import {queryUserTaskAuditLogs} from './user-task';
import {deleteProcessInstance} from './process-instance';
import {createDeployment, deleteResource, getResource, getResourceContent} from './resource';

const endpoints = {
	queryAuditLogs,
	getAuditLog,
	queryUserTaskAuditLogs,
	deleteProcessInstance,
	createDeployment,
	deleteResource,
	getResource,
	getResourceContent,
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
	userTaskAuditLogFilterSchema,
	queryUserTaskAuditLogsRequestBodySchema,
	queryUserTaskAuditLogsResponseBodySchema,
	queryUserTaskAuditLogs,
	type UserTaskAuditLogFilter,
	type QueryUserTaskAuditLogsRequestBody,
	type QueryUserTaskAuditLogsResponseBody,
} from './user-task';

export {deleteProcessInstanceRequestBodySchema, type DeleteProcessInstanceRequestBody} from './process-instance';

export {
	createDeploymentResponseBodySchema,
	deleteResourceRequestBodySchema,
	deleteResourceResponseBodySchema,
	batchOperationCreatedResultSchema,
	resourceSchema,
	getResourceContentResponseBodySchema,
	processDeploymentSchema,
	decisionDeploymentSchema,
	decisionRequirementsDeploymentSchema,
	formDeploymentSchema,
	resourceDeploymentSchema,
	type CreateDeploymentResponseBody,
	type DeleteResourceRequestBody,
	type DeleteResourceResponseBody,
	type BatchOperationCreatedResult,
	type Resource,
	type GetResourceContentResponseBody,
	type ProcessDeployment,
	type DecisionDeployment,
	type DecisionRequirementsDeployment,
	type FormDeployment,
	type ResourceDeployment,
} from './resource';
