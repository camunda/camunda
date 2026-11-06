/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {keepPreviousData, useInfiniteQuery, useQuery, useQueryClient} from '@tanstack/react-query';
import {z} from 'zod';
import type {DecisionInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {useDiagramXml} from '#/operate/pages/Processes/useDiagramXml';
import {getProcessDefinitionName, isInstanceRunning} from '#/operate/shared/utils/processInstance';
import {createSortSearchParamSchema} from '#/shared/sortSearchParam';
import {useProcessInstancePage} from './useProcessInstancePage';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {useProcessInstanceModification} from './useProcessInstanceModification';
import {useIncidentsFilter} from './useIncidentsFilter';
import {POLLING_INTERVAL_MS} from './processInstance.queries';
import {
	incidentDecisionInstancesQuery,
	incidentProcessInstancesQuery,
	hasShrunkRetainedPages,
	incidentsQuery,
	type IncidentsSort,
} from './incidents.queries';
import type {ChildInstanceWithIncident, DecisionInstanceLookup} from './incidents.types';

const DEFAULT_SORT: IncidentsSort = [{field: 'creationTime', order: 'DESC'}];
const sortSearchParamSchema = createSortSearchParamSchema(z.enum(['errorType', 'creationTime']));

function sortByRootCause(decisionInstances: DecisionInstance[]) {
	return [...decisionInstances].sort(
		(a, b) =>
			Number(a.state === 'FAILED') - Number(b.state === 'FAILED') || a.evaluationDate.localeCompare(b.evaluationDate),
	);
}

function useIncidentsTab() {
	const {processInstanceId, processInstance, search} = useProcessInstancePage();
	const {
		selectedElementId,
		selectedElementInstanceKey,
		resolvedElementInstance,
		isFetchingElement,
		isSelected,
		selectElementInstance,
		clearSelection,
	} = useProcessInstanceElementSelection();
	const {isModificationModeEnabled} = useProcessInstanceModification();
	const {errorTypes} = useIncidentsFilter();
	const resolvedElementInstanceKey = resolvedElementInstance?.elementInstanceKey;
	const isElementInstanceSelected =
		resolvedElementInstanceKey !== undefined && resolvedElementInstanceKey !== processInstanceId;
	const parsedSort = sortSearchParamSchema.safeParse(search.sort).data;
	const sort = parsedSort === undefined ? DEFAULT_SORT : [parsedSort];

	const queryClient = useQueryClient();
	const isPolling = isInstanceRunning(processInstance) && !isModificationModeEnabled;
	const refetchInterval = isPolling ? POLLING_INTERVAL_MS : false;
	const incidentsQueryOptions = incidentsQuery({
		processInstanceKey: processInstanceId,
		elementInstanceKey: isElementInstanceSelected ? resolvedElementInstanceKey : undefined,
		elementId: selectedElementId ?? undefined,
		errorTypes,
		sort,
		enabled: processInstance.hasIncident && (isElementInstanceSelected || !isFetchingElement),
		isPolling,
	});
	const result = useInfiniteQuery(incidentsQueryOptions);
	const shouldRestartPaging = result.data !== undefined && hasShrunkRetainedPages(result.data);
	useEffect(() => {
		if (shouldRestartPaging) {
			void queryClient.resetQueries({queryKey: incidentsQueryOptions.queryKey, exact: true});
		}
	}, [shouldRestartPaging, queryClient, incidentsQueryOptions.queryKey]);
	const incidents = result.data?.pages.flatMap((page) => page.items) ?? [];
	const totalIncidentsCount = result.data?.pages[0]?.page.totalItems ?? 0;

	const businessObjects = useDiagramXml(processInstance.processDefinitionKey).data?.businessObjects;
	const instancesWithIncident = Array.from(
		new Set(
			incidents
				.map((incident) => incident.processInstanceKey)
				.filter((processInstanceKey) => processInstanceKey !== processInstanceId),
		),
	);
	const {data: processDefinitionNames} = useQuery({
		...incidentProcessInstancesQuery({processInstanceKey: {$in: instancesWithIncident}}, instancesWithIncident.length),
		enabled: instancesWithIncident.length > 0,
		select: (response) =>
			Object.fromEntries(response.items.map((item) => [item.processInstanceKey, getProcessDefinitionName(item)])),
	});

	const decisionIncidentElementKeys = Array.from(
		new Set(
			incidents
				.filter(({errorType}) => errorType === 'DECISION_EVALUATION_ERROR' || errorType === 'CALLED_DECISION_ERROR')
				.map((incident) => incident.elementInstanceKey),
		),
	);
	const {data: decisionInstancesByElementKey = {}} = useQuery({
		...incidentDecisionInstancesQuery({elementInstanceKey: {$in: decisionIncidentElementKeys}}),
		enabled: decisionIncidentElementKeys.length > 0,
		placeholderData: keepPreviousData,
		refetchInterval,
		select: (response): DecisionInstanceLookup =>
			Object.fromEntries(
				sortByRootCause(response.items).map((instance) => [
					instance.elementInstanceKey,
					{
						decisionInstanceKey: instance.decisionEvaluationInstanceKey,
						decisionDefinitionName: instance.decisionDefinitionName,
					},
				]),
			),
	});

	const hasIncidentFromChild =
		isElementInstanceSelected &&
		resolvedElementInstance?.hasIncident === true &&
		result.isSuccess &&
		totalIncidentsCount === 0;
	const {data: calledProcessInstance} = useQuery({
		...incidentProcessInstancesQuery({parentElementInstanceKey: resolvedElementInstanceKey, hasIncident: true}),
		enabled: hasIncidentFromChild && resolvedElementInstance?.type === 'CALL_ACTIVITY',
		refetchInterval,
		select: (response) => response.items[0],
	});
	const {data: calledDecisionInstance} = useQuery({
		...incidentDecisionInstancesQuery({elementInstanceKey: resolvedElementInstanceKey}),
		enabled: hasIncidentFromChild && resolvedElementInstance?.type === 'BUSINESS_RULE_TASK',
		refetchInterval,
		select: (response) => sortByRootCause(response.items).at(-1),
	});
	const childInstanceWithIncident: ChildInstanceWithIncident | undefined =
		calledProcessInstance !== undefined
			? {
					type: 'process',
					key: calledProcessInstance.processInstanceKey,
					name: getProcessDefinitionName(calledProcessInstance),
				}
			: calledDecisionInstance !== undefined
				? {
						type: 'decision',
						key: calledDecisionInstance.decisionEvaluationInstanceKey,
						name: calledDecisionInstance.decisionDefinitionName,
					}
				: undefined;

	const enhancedIncidents = incidents.map((incident) => ({
		...incident,
		elementName: businessObjects?.[incident.elementId]?.name ?? incident.elementId,
		isSelected:
			selectedElementInstanceKey === null
				? isSelected(incident.elementId)
				: incident.elementInstanceKey === selectedElementInstanceKey,
		processDefinitionName: processDefinitionNames?.[incident.processInstanceKey] ?? '',
	}));

	return {
		processInstanceId,
		tenantId: search.tenantId,
		isProcessInstanceSuspended: processInstance.state === 'SUSPENDED',
		state:
			result.status === 'pending'
				? ('skeleton' as const)
				: result.status === 'error'
					? ('error' as const)
					: totalIncidentsCount === 0
						? ('empty' as const)
						: ('content' as const),
		incidents: enhancedIncidents,
		totalIncidentsCount,
		hasMoreTotalItems: result.data?.pages[0]?.page.hasMoreTotalItems,
		decisionInstancesByElementKey,
		childInstanceWithIncident,
		hasPreviousPage: result.hasPreviousPage,
		hasNextPage: result.hasNextPage,
		isFetchingPreviousPage: result.isFetchingPreviousPage,
		isFetchingNextPage: result.isFetchingNextPage,
		selectIncident: (incident: {elementId: string; elementInstanceKey: string}) => {
			const selectedIncidents = enhancedIncidents.filter((item) => item.isSelected);
			if (selectedIncidents.length === 1 && selectedIncidents[0]?.elementInstanceKey === incident.elementInstanceKey) {
				clearSelection();
			} else {
				void selectElementInstance({elementId: incident.elementId, elementInstanceKey: incident.elementInstanceKey});
			}
		},
		fetchPreviousPage: async () => {
			if (!result.hasPreviousPage || result.isFetchingPreviousPage) {
				return false;
			}
			await result.fetchPreviousPage();
			return true;
		},
		fetchNextPage: () => {
			if (result.hasNextPage && !result.isFetchingNextPage) {
				void result.fetchNextPage();
			}
		},
	};
}

export {useIncidentsTab};
