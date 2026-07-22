/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {queryOptions, useQuery} from '@tanstack/react-query';
import type {
	GetProcessDefinitionStatisticsRequestBody,
	GetProcessDefinitionStatisticsResponseBody,
	ProcessDefinitionStatistic,
} from '@camunda/camunda-api-zod-schemas/8.10';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {request} from '#/shared/http/request';
import {endpoints} from '#/shared/http/endpoints';
import type {OverlayData} from '#/operate/shared/Diagram/overlayTypes';
import {isProcessOrSubProcessEndEvent, getSubprocessOverlayFromIncidentElements} from '#/operate/shared/utils/elements';
import {
	ACTIVE_BADGE,
	CANCELED_BADGE,
	COMPLETED_BADGE,
	COMPLETED_END_EVENT_BADGE,
	INCIDENTS_BADGE,
} from '#/operate/shared/utils/badgePositions';

type ElementState = keyof Omit<ProcessDefinitionStatistic, 'elementId'>;

const overlayPositions: Record<
	ElementState | 'completedEndEvents',
	{top?: number; bottom?: number; left?: number; right?: number}
> = {
	active: ACTIVE_BADGE,
	incidents: INCIDENTS_BADGE,
	canceled: CANCELED_BADGE,
	completed: COMPLETED_BADGE,
	completedEndEvents: COMPLETED_END_EVENT_BADGE,
};

function statisticsOverlaysParser(businessObjects: BusinessObjects) {
	return (data: GetProcessDefinitionStatisticsResponseBody): OverlayData[] => {
		const incidentElements: BusinessObjects[string][] = [];

		const overlays = data.items.flatMap((statistic) => {
			const states: ElementState[] = ['active', 'incidents', 'canceled', 'completed'];

			return states.flatMap((elementState) => {
				const count = statistic[elementState];
				if (count <= 0) {
					return [];
				}

				const businessObject = businessObjects[statistic.elementId];

				if (elementState === 'incidents' && businessObject) {
					incidentElements.push(businessObject);
				}

				const resolvedState =
					elementState === 'completed' && businessObject && isProcessOrSubProcessEndEvent(businessObject)
						? 'completedEndEvents'
						: elementState;

				if (resolvedState === 'completed') {
					return [];
				}

				return [
					{
						payload: {elementState: resolvedState, count},
						type: `statistics-${resolvedState}`,
						elementId: statistic.elementId,
						position: overlayPositions[resolvedState],
					},
				];
			});
		});

		return [...overlays, ...getSubprocessOverlayFromIncidentElements(incidentElements, 'statistics-incidents')];
	};
}

function diagramStatisticsOverlaysQuery(
	processDefinitionKey: string,
	filter: GetProcessDefinitionStatisticsRequestBody['filter'],
	businessObjects: BusinessObjects,
) {
	return queryOptions({
		queryKey: ['processDefinitionStatistics', processDefinitionKey, filter] as const,
		queryFn: async () => {
			const {response, error} = await request(endpoints.getProcessDefinitionStatistics({processDefinitionKey, filter}));
			if (error !== null) {
				throw error;
			}
			return response.json() as Promise<GetProcessDefinitionStatisticsResponseBody>;
		},
		select: statisticsOverlaysParser(businessObjects),
		refetchInterval: 5000,
	});
}

function useDiagramStatisticsOverlays({
	processDefinitionKey,
	filter,
	businessObjects,
	enabled,
}: {
	processDefinitionKey?: string;
	filter: GetProcessDefinitionStatisticsRequestBody['filter'];
	businessObjects?: BusinessObjects;
	enabled: boolean;
}) {
	return useQuery({
		...diagramStatisticsOverlaysQuery(processDefinitionKey ?? '', filter, businessObjects ?? {}),
		enabled: enabled && processDefinitionKey !== undefined && businessObjects !== undefined,
	});
}

export {useDiagramStatisticsOverlays};
