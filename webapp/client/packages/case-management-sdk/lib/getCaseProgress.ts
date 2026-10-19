/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CaseModel, Stage} from './parseCaseModel';

type ElementInstanceLike = {
	elementId: string;
	elementInstanceKey: string;
	state: 'ACTIVE' | 'COMPLETED' | 'TERMINATED';
	startDate: string;
	endDate?: string | null;
	hasIncident: boolean;
};

type ProcessInstanceState = 'ACTIVE' | 'COMPLETED' | 'TERMINATED' | 'SUSPENDED';

type StageState = 'done' | 'inProgress' | 'incident' | 'cancelled' | 'skipped' | 'ahead';

type StageProgress = {
	stage: Stage;
	state: StageState;
	/** The start and end of the latest run of the stage; a loop back runs a stage again. */
	startDate: string | null;
	endDate: string | null;
	/** The latest element instance of the stage; ad-hoc activation needs it. */
	elementInstanceKey: string | null;
};

const STARTED_STATES = new Set<StageState>(['done', 'inProgress', 'incident', 'cancelled']);

/** Dates can come with different offsets from different searches, so the SDK compares timestamps. */
function toTime(date: string | null | undefined): number {
	return date === null || date === undefined ? 0 : Date.parse(date) || 0;
}

function byStartDate(first: ElementInstanceLike, second: ElementInstanceLike): number {
	return toTime(first.startDate) - toTime(second.startDate);
}

function getStageProgress(stage: Stage, elementInstances: ElementInstanceLike[]): StageProgress {
	const instances = elementInstances.filter(({elementId}) => elementId === stage.id).sort(byStartDate);
	const latest = instances.at(-1);

	if (latest === undefined) {
		return {stage, state: 'ahead', startDate: null, endDate: null, elementInstanceKey: null};
	}

	const progress = {
		stage,
		startDate: latest.startDate,
		endDate: latest.endDate ?? null,
		elementInstanceKey: latest.elementInstanceKey,
	};

	if (latest.state === 'ACTIVE') {
		const memberIds = new Set([stage.id, ...stage.elementIds]);
		const hasIncident = elementInstances.some(
			({elementId, state, hasIncident}) => memberIds.has(elementId) && state === 'ACTIVE' && hasIncident,
		);

		return {...progress, state: hasIncident ? 'incident' : 'inProgress'};
	}

	if (latest.state === 'COMPLETED') {
		return {...progress, state: 'done'};
	}

	// "Stage done" terminates the stage on purpose, so it reads as done, not as cancelled (STAGES.md)
	const boundaryEventId = stage.manualCompletion?.boundaryEventId;
	const wasDoneManually = elementInstances.some(
		({elementId, startDate}) => elementId === boundaryEventId && toTime(startDate) >= toTime(latest.startDate),
	);

	return {...progress, state: wasDoneManually ? 'done' : 'cancelled'};
}

/**
 * A loop back to an earlier stage (rework) starts it again. The stages after it that ended before this
 * new start must run again, so they read as ahead, not as done.
 */
function resetReworkedStages(mainStages: StageProgress[]): StageProgress[] {
	const currentIndex = mainStages.findIndex(({state}) => state === 'inProgress' || state === 'incident');
	const reworkStart = mainStages[currentIndex]?.startDate;

	if (currentIndex < 0 || reworkStart === undefined || reworkStart === null) {
		return mainStages;
	}

	return mainStages.map((stageProgress, index) =>
		index > currentIndex && stageProgress.endDate !== null && toTime(stageProgress.endDate) <= toTime(reworkStart)
			? {...stageProgress, state: 'ahead', startDate: null, endDate: null, elementInstanceKey: null}
			: stageProgress,
	);
}

/**
 * Derives the line of stages of a case. An alternative stage that the case entered replaces the
 * stages that did not start (CASE_UI.md: "an alternative path replaces the tail of the line").
 */
function getCaseProgress(
	model: CaseModel,
	elementInstances: ElementInstanceLike[],
	processInstanceState: ProcessInstanceState,
): StageProgress[] {
	const progress = model.stages.map((stage) => getStageProgress(stage, elementInstances));
	const mainStages = resetReworkedStages(
		progress.filter(({stage}) => stage.kind === 'subProcess' || stage.kind === 'adHoc'),
	);
	const enteredAlternatives = progress.filter(
		({stage, state}) => stage.kind === 'alternative' && STARTED_STATES.has(state),
	);

	if (enteredAlternatives.length > 0) {
		return [...mainStages.filter(({state}) => STARTED_STATES.has(state)), ...enteredAlternatives].sort(
			(first, second) => toTime(first.startDate) - toTime(second.startDate),
		);
	}

	const isFinished = processInstanceState === 'COMPLETED' || processInstanceState === 'TERMINATED';

	return mainStages.map((stageProgress, index) => {
		const hasLaterStarted = mainStages.slice(index + 1).some(({state}) => STARTED_STATES.has(state));

		return stageProgress.state === 'ahead' && (hasLaterStarted || isFinished)
			? {...stageProgress, state: 'skipped'}
			: stageProgress;
	});
}

function getCurrentStage(progress: StageProgress[]): StageProgress | null {
	return progress.find(({state}) => state === 'inProgress' || state === 'incident') ?? null;
}

export {getCaseProgress, getCurrentStage};
export type {ElementInstanceLike, ProcessInstanceState, StageProgress, StageState};
