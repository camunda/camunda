/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions} from '@tanstack/react-query';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';

type ActorType = 'USER' | 'CLIENT' | 'ENGINE';

type VariableHistoryItem = {
	variableHistoryItemKey: string;
	variableKey: string;
	name: string;
	value: string;
	isTruncated: boolean;
	previousValue: string | null;
	isPreviousValueTruncated: boolean;
	metadata: {protectionModes: 'REDACT'[]};
	scopeKey: string;
	elementInstanceKey: string | null;
	elementId: string | null;
	processInstanceKey: string;
	rootProcessInstanceKey: string;
	processDefinitionKey: string;
	processDefinitionId: string;
	tenantId: string;
	timestamp: string;
	operationType: string;
	actorType: ActorType;
	actorId: string | null;
	agentElementId: string | null;
};

type VariableHistoryItemDetail = Omit<VariableHistoryItem, 'isTruncated' | 'isPreviousValueTruncated'>;

type VariableHistorySearchResponse = {
	items: VariableHistoryItem[];
	tracing: {user: boolean; client: boolean; engine: boolean};
	page: {totalItems: number; hasMoreTotalItems: boolean; startCursor: string | null; endCursor: string | null};
};

type ProcessVariable = {
	variableKey: string;
	name: string;
	value: string;
	isTruncated: boolean;
	scopeKey: string;
	processInstanceKey: string;
};

type VariablesSearchResponse = {
	items: ProcessVariable[];
	page: {totalItems: number};
};

async function post<T>(url: string, body: unknown): Promise<T> {
	const {response, error} = await request(
		new Request(url, {
			method: 'POST',
			headers: {'Content-Type': 'application/json'},
			body: JSON.stringify(body),
		}),
	);
	if (error !== null) {
		throw mapQueryError(error);
	}
	return response.json();
}

function variablesQuery(processInstanceKey: string) {
	return queryOptions({
		queryKey: ['variableHistoryPrototype', 'variables', processInstanceKey] as const,
		queryFn: () => post<VariablesSearchResponse>('/v2/variables/search', {filter: {processInstanceKey}}),
	});
}

type VariableHistoryTarget = {processInstanceKey: string; scopeKey: string; name: string};

function variableHistoryQuery(target: VariableHistoryTarget) {
	return queryOptions({
		queryKey: ['variableHistoryPrototype', 'search', target] as const,
		queryFn: () =>
			post<VariableHistorySearchResponse>('/v2/variables/history/search?truncateValues=true', {
				filter: target,
				sort: [{field: 'timestamp', order: 'ASC'}],
				page: {limit: 1000},
			}),
	});
}

function variableHistoryItemQuery(variableHistoryItemKey: string) {
	return queryOptions({
		queryKey: ['variableHistoryPrototype', 'item', variableHistoryItemKey] as const,
		queryFn: async (): Promise<VariableHistoryItemDetail> => {
			const {response, error} = await request(new Request(`/v2/variables/history/${variableHistoryItemKey}`));
			if (error !== null) {
				throw mapQueryError(error);
			}
			return response.json();
		},
	});
}

export type {VariableHistoryTarget};
export {variablesQuery, variableHistoryQuery, variableHistoryItemQuery};
export type {VariableHistoryItem, VariableHistoryItemDetail, ProcessVariable, ActorType};
