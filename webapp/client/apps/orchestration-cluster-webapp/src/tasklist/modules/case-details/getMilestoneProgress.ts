/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ElementInstance, ProcessDefinitionStatistic, ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import type {Milestone} from '#/tasklist/modules/case-details/parseCaseModel';

type MilestoneState = 'blocked' | 'active' | 'completed' | 'skipped' | 'notStarted';

type MilestoneProgress = Milestone & {
	state: MilestoneState;
	startDate: string | null;
	endDate: string | null;
};

const STARTED_STATES = new Set<MilestoneState>(['blocked', 'active', 'completed']);

function getStartedState(elementIds: string[], statistics: Map<string, ProcessDefinitionStatistic>): MilestoneState {
	const memberStatistics = elementIds.flatMap((id) => statistics.get(id) ?? []);

	if (memberStatistics.some(({incidents}) => incidents > 0)) {
		return 'blocked';
	}

	if (memberStatistics.some(({active}) => active > 0)) {
		return 'active';
	}

	if (memberStatistics.some(({completed, canceled}) => completed + canceled > 0)) {
		return 'completed';
	}

	return 'notStarted';
}

function getDateRange(
	elementIds: string[],
	elementInstances: ElementInstance[],
): Pick<MilestoneProgress, 'startDate' | 'endDate'> {
	const memberIds = new Set(elementIds);
	const members = elementInstances.filter(({elementId}) => memberIds.has(elementId));
	const startDates = members.map(({startDate}) => startDate).sort();
	const endDates = members.flatMap(({endDate}) => endDate ?? []).sort();

	return {
		startDate: startDates[0] ?? null,
		endDate: members.some(({endDate}) => endDate === null) ? null : (endDates.at(-1) ?? null),
	};
}

function getMilestoneProgress(
	milestones: Milestone[],
	statistics: ProcessDefinitionStatistic[],
	elementInstances: ElementInstance[],
	processInstanceState: ProcessInstance['state'],
): MilestoneProgress[] {
	const statisticsByElement = new Map(statistics.map((statistic) => [statistic.elementId, statistic]));
	const isProcessFinished = processInstanceState === 'COMPLETED' || processInstanceState === 'TERMINATED';
	const startedStates = milestones.map(({elementIds}) => getStartedState(elementIds, statisticsByElement));

	return milestones.map((milestone, index) => {
		const startedState = startedStates[index] ?? 'notStarted';
		const hasLaterStarted = startedStates.slice(index + 1).some((state) => STARTED_STATES.has(state));
		const state = startedState === 'notStarted' && (hasLaterStarted || isProcessFinished) ? 'skipped' : startedState;

		return {...milestone, state, ...getDateRange(milestone.elementIds, elementInstances)};
	});
}

export {getMilestoneProgress};
export type {MilestoneProgress, MilestoneState};
