/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {queryOptions, useQuery} from '@tanstack/react-query';
import type {
	AgentInstance,
	GetProcessInstanceStatisticsResponseBody,
	GetProcessInstanceSequenceFlowsResponseBody,
	QueryAgentInstancesResponseBody,
	QueryElementInstancesResponseBody,
	QueryProcessInstancesResponseBody,
	QueryDecisionInstancesResponseBody,
	ProcessInstance,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {endpoints} from '#/shared/http/endpoints';
import {request} from '#/shared/http/request';
import {mapQueryError} from '#/shared/http/mapQueryError';
import {isInstanceRunning} from '#/operate/shared/utils/processInstance';

const POLLING_INTERVAL_MS = 5000;
const ACTIVE_AGENT_STATUSES = ['INITIALIZING', 'TOOL_DISCOVERY', 'THINKING', 'TOOL_CALLING'] as const;

async function getJson<T>(endpoint: Request): Promise<T> {
	const {response, error} = await request(endpoint);
	if (error !== null) {
		throw mapQueryError(error);
	}
	return response.json();
}

async function getItems<T extends {items: unknown[]}>(endpoint: Request): Promise<T['items']> {
	return (await getJson<T>(endpoint)).items;
}

function instanceAgentInstancesQuery(processInstanceKey: string) {
	return queryOptions({
		queryKey: ['instanceDiagramAgents', processInstanceKey] as const,
		queryFn: async () => {
			const items: AgentInstance[] = [];
			let from = 0;
			let hasMore = true;
			while (hasMore) {
				const result = await getJson<QueryAgentInstancesResponseBody>(
					endpoints.queryAgentInstances({
						filter: {processInstanceKey, status: {$in: [...ACTIVE_AGENT_STATUSES]}},
						page: {from, limit: 100},
					}),
				);
				items.push(...result.items);
				from += result.items.length;
				hasMore = result.page.hasMoreTotalItems && result.items.length > 0;
			}
			return items;
		},
	});
}

function elementDrilldownQuery(processInstanceKey: string, elementId: string) {
	return queryOptions({
		queryKey: ['instanceDiagramDrilldownElement', processInstanceKey, elementId] as const,
		queryFn: async () =>
			getJson<QueryElementInstancesResponseBody>(
				endpoints.queryElementInstances({filter: {processInstanceKey, elementId}, page: {limit: 1}}),
			),
	});
}

function calledProcessQuery(elementInstanceKey: string) {
	return queryOptions({
		queryKey: ['instanceDiagramCalledProcess', elementInstanceKey] as const,
		queryFn: async () =>
			getJson<QueryProcessInstancesResponseBody>(
				endpoints.queryProcessInstances({filter: {parentElementInstanceKey: elementInstanceKey}, page: {limit: 1}}),
			),
	});
}

function calledDecisionQuery(elementInstanceKey: string) {
	return queryOptions({
		queryKey: ['instanceDiagramCalledDecision', elementInstanceKey] as const,
		queryFn: async () =>
			getJson<QueryDecisionInstancesResponseBody>(
				endpoints.queryDecisionInstances({filter: {elementInstanceKey}, page: {limit: 1}}),
			),
	});
}

function useInstanceDiagramData(instance: ProcessInstance, hasDiagram: boolean) {
	const processInstanceKey = instance.processInstanceKey;
	const isRunning = isInstanceRunning(instance);
	const previousInstance = useRef({processInstanceKey, isRunning});
	const refetchInterval = isRunning ? POLLING_INTERVAL_MS : false;
	const statistics = useQuery({
		queryKey: ['instanceDiagramStatistics', processInstanceKey],
		queryFn: () =>
			getItems<GetProcessInstanceStatisticsResponseBody>(endpoints.getProcessInstanceStatistics(processInstanceKey)),
		refetchInterval,
		enabled: hasDiagram,
	});
	const sequenceFlows = useQuery({
		queryKey: ['instanceDiagramSequenceFlows', processInstanceKey],
		queryFn: () =>
			getItems<GetProcessInstanceSequenceFlowsResponseBody>(
				endpoints.getProcessInstanceSequenceFlows(processInstanceKey),
			),
		refetchInterval,
		enabled: hasDiagram,
	});
	const agents = useQuery({
		...instanceAgentInstancesQuery(processInstanceKey),
		refetchInterval,
		enabled: hasDiagram && isRunning,
	});
	const refetchStatistics = statistics.refetch;
	const refetchSequenceFlows = sequenceFlows.refetch;
	useEffect(() => {
		if (
			hasDiagram &&
			previousInstance.current.processInstanceKey === processInstanceKey &&
			previousInstance.current.isRunning &&
			!isRunning
		) {
			void refetchStatistics();
			void refetchSequenceFlows();
		}
		previousInstance.current = {processInstanceKey, isRunning};
	}, [hasDiagram, isRunning, processInstanceKey, refetchStatistics, refetchSequenceFlows]);
	return {statistics, sequenceFlows, agents};
}

export {useInstanceDiagramData, elementDrilldownQuery, calledProcessQuery, calledDecisionQuery};
