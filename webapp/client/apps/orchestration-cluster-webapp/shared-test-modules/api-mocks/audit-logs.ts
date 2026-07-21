/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
	AuditLog,
	QueryAuditLogsResponseBody,
	QueryUserTaskAuditLogsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {createPaginatedResponse} from './shared';

function createAuditLog(overrides?: Partial<AuditLog>): AuditLog {
	return {
		auditLogKey: '2251799813685283',
		entityKey: '2251799813685281',
		entityType: 'USER_TASK',
		operationType: 'CREATE',
		batchOperationKey: null,
		batchOperationType: null,
		timestamp: '2024-01-01T10:00:00.000Z',
		actorId: 'demo',
		actorType: 'USER',
		tenantId: '<default>',
		result: 'SUCCESS',
		category: 'USER_TASKS',
		processDefinitionId: null,
		processDefinitionKey: null,
		processInstanceKey: null,
		rootProcessInstanceKey: null,
		elementInstanceKey: null,
		jobKey: null,
		userTaskKey: '2251799813685281',
		decisionRequirementsId: null,
		decisionRequirementsKey: null,
		decisionDefinitionId: null,
		decisionDefinitionKey: null,
		decisionEvaluationKey: null,
		deploymentKey: null,
		formKey: null,
		resourceKey: null,
		relatedEntityKey: null,
		relatedEntityType: null,
		entityDescription: null,
		agentElementId: null,
		inboundChannelType: null,
		inboundChannelToolName: null,
		...overrides,
	};
}

function createQueryUserTaskAuditLogsResponse(overrides?: {
	items?: AuditLog[];
	page?: Partial<QueryUserTaskAuditLogsResponseBody['page']>;
}): QueryUserTaskAuditLogsResponseBody {
	const items = overrides?.items ?? [];

	return createPaginatedResponse<AuditLog>({
		items,
		page: {
			totalItems: items.length,
			startCursor: null,
			endCursor: null,
			hasMoreTotalItems: false,
			...overrides?.page,
		},
	});
}

function createQueryAuditLogsResponse(overrides?: {
	items?: AuditLog[];
	page?: Partial<QueryAuditLogsResponseBody['page']>;
}): QueryAuditLogsResponseBody {
	const items = overrides?.items ?? [];

	return createPaginatedResponse<AuditLog>({
		items,
		page: {
			totalItems: items.length,
			startCursor: null,
			endCursor: null,
			hasMoreTotalItems: false,
			...overrides?.page,
		},
	});
}

export {createAuditLog, createQueryUserTaskAuditLogsResponse, createQueryAuditLogsResponse};
