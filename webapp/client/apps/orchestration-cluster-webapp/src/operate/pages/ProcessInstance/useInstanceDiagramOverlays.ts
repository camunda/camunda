/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import type {
	AgentInstance,
	ProcessDefinitionStatistic,
	WaitStateStatistic,
} from '@camunda/camunda-api-zod-schemas/8.10';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import type {OverlayData} from '#/operate/shared/Diagram/overlayTypes';
import {getSubprocessOverlayFromIncidentElements, isProcessOrSubProcessEndEvent} from '#/operate/shared/utils/elements';
import {
	ACTIVE_BADGE,
	CANCELED_BADGE,
	COMPLETED_BADGE,
	COMPLETED_END_EVENT_BADGE,
	INCIDENTS_BADGE,
} from '#/operate/shared/utils/badgePositions';
import {getWaitStateLabel} from '#/operate/shared/utils/waitStates';

type Props = {
	statistics?: ProcessDefinitionStatistic[];
	waitStates?: WaitStateStatistic[];
	agents?: AgentInstance[];
	businessObjects?: BusinessObjects;
	processDefinitionId: string;
	isModificationModeEnabled: boolean;
	isExecutionCountVisible: boolean;
	modificationBadges: ModificationBadge[];
};

type ModificationBadge = OverlayData & {
	type: 'instance-modification';
	payload: {newTokenCount: number; cancelledTokenCount: number};
};

type InstanceState = 'active' | 'incidents' | 'canceled' | 'completed' | 'completedEndEvents';

const POSITIONS = {
	active: ACTIVE_BADGE,
	incidents: INCIDENTS_BADGE,
	canceled: CANCELED_BADGE,
	completed: COMPLETED_BADGE,
	completedEndEvents: COMPLETED_END_EVENT_BADGE,
};

const NARROW_TYPES = new Set([
	'bpmn:ExclusiveGateway',
	'bpmn:ParallelGateway',
	'bpmn:InclusiveGateway',
	'bpmn:EventBasedGateway',
	'bpmn:ComplexGateway',
	'bpmn:StartEvent',
	'bpmn:EndEvent',
	'bpmn:IntermediateCatchEvent',
	'bpmn:IntermediateThrowEvent',
	'bpmn:BoundaryEvent',
]);

const STATUS_IMPORTANCE: Record<AgentInstance['status'], number> = {
	INITIALIZING: 4,
	THINKING: 3,
	TOOL_DISCOVERY: 2,
	TOOL_CALLING: 1,
	COMPLETED: 0,
	UNKNOWN: 0,
	IDLE: 0,
};

function useInstanceDiagramOverlays({
	statistics,
	waitStates,
	agents,
	businessObjects,
	processDefinitionId,
	isModificationModeEnabled,
	isExecutionCountVisible,
	modificationBadges,
}: Props) {
	return useMemo(() => {
		const incidentElements =
			statistics?.filter(({incidents}) => incidents > 0).map(({elementId}) => businessObjects?.[elementId]) ?? [];
		const stateOverlays: OverlayData[] = (statistics ?? []).flatMap((statistic) => {
			const businessObject = businessObjects?.[statistic.elementId];
			if (!businessObject) {
				return [];
			}
			const isProcessEndEvent =
				isProcessOrSubProcessEndEvent(businessObject) && businessObject.$parent?.$type === 'bpmn:Process';
			return (['active', 'incidents', 'canceled', 'completed'] as const).flatMap((state) => {
				const count = statistic[state];
				if (
					count <= 0 ||
					(isModificationModeEnabled && (state === 'completed' || state === 'canceled')) ||
					(!isExecutionCountVisible && state === 'completed' && !isProcessEndEvent)
				) {
					return [];
				}
				const elementState: InstanceState = state === 'completed' && isProcessEndEvent ? 'completedEndEvents' : state;
				return [
					{
						elementId: statistic.elementId,
						type: 'instance-state',
						position: POSITIONS[elementState],
						payload: {elementState, count},
					},
				];
			});
		});
		stateOverlays.push(...getSubprocessOverlayFromIncidentElements(incidentElements, 'instance-state'));

		const elementsInStats = new Set(statistics?.map(({elementId}) => elementId));
		for (const {elementId, waitingCount} of waitStates ?? []) {
			if (
				waitingCount > 0 &&
				!elementsInStats.has(elementId) &&
				businessObjects?.[elementId]?.loopCharacteristics?.$type === 'bpmn:MultiInstanceLoopCharacteristics'
			) {
				stateOverlays.push({
					elementId,
					type: 'instance-state',
					position: ACTIVE_BADGE,
					payload: {elementState: 'active'},
				});
			}
		}
		if (isModificationModeEnabled) {
			return [...stateOverlays, ...modificationBadges];
		}

		const agentByElement = new Map<
			string,
			{agentInstanceKey: string; status: AgentInstance['status']; additionalActiveCount: number}
		>();
		for (const agent of agents ?? []) {
			const current = agentByElement.get(agent.elementId);
			if (!current) {
				agentByElement.set(agent.elementId, {
					agentInstanceKey: agent.agentInstanceKey,
					status: agent.status,
					additionalActiveCount: 0,
				});
			} else {
				current.additionalActiveCount += 1;
				if (STATUS_IMPORTANCE[agent.status] > STATUS_IMPORTANCE[current.status]) {
					current.agentInstanceKey = agent.agentInstanceKey;
					current.status = agent.status;
				}
			}
		}

		const waitingOverlays: OverlayData[] = (waitStates ?? []).flatMap(({elementId, waitingCount}) => {
			if (elementId === processDefinitionId || agentByElement.has(elementId)) {
				return [];
			}
			const label = getWaitStateLabel(waitingCount);
			if (label === null) {
				return [];
			}
			const isNarrow = NARROW_TYPES.has(businessObjects?.[elementId]?.$type ?? '');
			return [
				{
					elementId,
					type: 'instance-waiting',
					position: isNarrow ? {top: -36, left: 18} : {top: -36, left: 0},
					payload: {label, centered: isNarrow},
				},
			];
		});

		const agentOverlays: OverlayData[] = Array.from(agentByElement.entries()).flatMap(([elementId, payload]) => [
			{elementId, type: 'instance-agent-status', position: {top: -32, left: 0}, payload},
			{elementId, type: 'instance-agent-shine', position: {top: 0, left: 0}, payload},
		]);

		return [...stateOverlays, ...waitingOverlays, ...agentOverlays];
	}, [
		statistics,
		waitStates,
		agents,
		businessObjects,
		processDefinitionId,
		isModificationModeEnabled,
		isExecutionCountVisible,
		modificationBadges,
	]);
}

export {useInstanceDiagramOverlays};
export type {ModificationBadge};
