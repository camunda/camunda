/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import type {
	MessageSubscription,
	QueryMessageSubscriptionsRequestBody,
	QueryMessageSubscriptionsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {DEFAULT_PAGE_SIZE, type McpProcessesSearch} from './searchSchema';

// A blank property carries no more information than an absent one, and the UI renders both
// as "no information provided" — so normalize rather than reject, which would fail the parse
// for the whole row.
const toolPropertySchema = z
	.string()
	.nullish()
	.transform((value) => {
		const trimmed = value?.trim() ?? '';
		return trimmed === '' ? null : trimmed;
	});

const toolPropertiesSchema = z
	.looseObject({
		'io.camunda.tool:purpose': toolPropertySchema,
		'io.camunda.tool:results': toolPropertySchema,
		'io.camunda.tool:when_to_use': toolPropertySchema,
		'io.camunda.tool:when_not_to_use': toolPropertySchema,
	})
	.transform((properties) => ({
		purpose: properties['io.camunda.tool:purpose'],
		results: properties['io.camunda.tool:results'],
		whenToUse: properties['io.camunda.tool:when_to_use'],
		whenNotToUse: properties['io.camunda.tool:when_not_to_use'],
	}))
	.catch({purpose: null, results: null, whenToUse: null, whenNotToUse: null});

type McpToolProperties = z.output<typeof toolPropertiesSchema>;

type McpProcessTool = {
	id: string;
	toolName: string;
	toolProperties: McpToolProperties;
	processDefinitionName: string;
	processDefinitionVersion: number | null;
	tenantId: string;
};

function getMcpProcessToolsRequestBody(search: McpProcessesSearch): QueryMessageSubscriptionsRequestBody {
	const pageSize = search.pageSize ?? DEFAULT_PAGE_SIZE;
	const searchTerm = search.search?.trim();

	return {
		sort: [{field: 'toolName', order: search.sortOrder ?? 'asc'}],
		filter: {
			messageSubscriptionType: 'START_EVENT',
			messageSubscriptionState: {$neq: 'DELETED'},
			toolName:
				searchTerm === undefined || searchTerm === '' ? {$exists: true} : {$exists: true, $like: `*${searchTerm}*`},
		},
		page: {
			from: ((search.page ?? 1) - 1) * pageSize,
			limit: pageSize,
		},
	};
}

// A subscription without a `toolName` is not an MCP tool. The request filters them out
// server-side, so this only guards against a response that ignores the filter.
function mapSubscriptionToTool(subscription: MessageSubscription): McpProcessTool | null {
	if (subscription.toolName === null) {
		return null;
	}

	return {
		id: subscription.messageSubscriptionKey,
		toolName: subscription.toolName,
		toolProperties: toolPropertiesSchema.parse(subscription.toolProperties),
		processDefinitionName: subscription.processDefinitionName ?? subscription.processDefinitionId,
		processDefinitionVersion: subscription.processDefinitionVersion,
		tenantId: subscription.tenantId,
	};
}

function mapResponseToTools(response: QueryMessageSubscriptionsResponseBody): McpProcessTool[] {
	return response.items.map(mapSubscriptionToTool).filter((tool) => tool !== null);
}

export {getMcpProcessToolsRequestBody, mapResponseToTools};
export type {McpProcessTool, McpToolProperties};
