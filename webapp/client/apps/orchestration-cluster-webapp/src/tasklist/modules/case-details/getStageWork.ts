/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CaseModel, StageProgress} from '@camunda/case-management-sdk';
import type {ElementInstance, UserTask, Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import {tryParseJSON} from '#/tasklist/modules/json/tryParseJSON';

type ActivityStatus = 'open' | 'active' | 'completed' | 'notStarted';

type StageActivity = {
	elementId: string;
	name: string;
	isRequired: boolean;
	/** The current state: an open task or a running instance wins over earlier completions. */
	status: ActivityStatus;
	/** How often the activity completed in this run of the stage, also while it runs again. */
	completedCount: number;
	/** The open user task of this activity, if it has one. */
	userTask: UserTask | null;
};

type StageCompletion = {
	messageName: string;
	doneElementId: string | null;
	/** `null` when the correlation key expression cannot be resolved from the root variables. */
	correlationKey: string | null;
	isAllowed: boolean;
};

type StageWork = {
	/** Activities of an ad-hoc stage: required first, then optional. */
	activities: StageActivity[];
	/** Open user tasks of the stage that are not an activity of the list above, and of optional stages. */
	openTasks: UserTask[];
	/** The optional stage of an open task, by user task key: it runs next to the active stage (GAP-001). */
	optionalStageNames: Record<string, string>;
	completion: StageCompletion | null;
};

const VARIABLE_REFERENCE = /^=\s*([A-Za-z_][A-Za-z0-9_]*)\s*$/;

// prototype: the engine evaluates the correlation key; the case page only resolves a plain variable reference
// such as `=caseId`, or a static key.
function resolveCorrelationKey(expression: string | null, rootVariables: Variable[]): string | null {
	if (expression === null) {
		return null;
	}

	if (!expression.startsWith('=')) {
		return expression;
	}

	const variableName = VARIABLE_REFERENCE.exec(expression)?.[1];
	const variable = rootVariables.find(({name}) => name === variableName);
	const value = variable === undefined ? undefined : tryParseJSON(variable.value);

	return typeof value === 'string' || typeof value === 'number' ? String(value) : null;
}

/**
 * Combines both searches: the user task search can show a completion before the element instance
 * search does, and the other way around.
 */
function getActivityState(
	elementId: string,
	stageInstances: ElementInstance[],
	stageTasks: UserTask[],
): Pick<StageActivity, 'status' | 'completedCount'> {
	const instances = stageInstances.filter((instance) => instance.elementId === elementId);
	const tasks = stageTasks.filter((task) => task.elementId === elementId);
	const completedKeys = new Set([
		...instances.filter(({state}) => state === 'COMPLETED').map(({elementInstanceKey}) => elementInstanceKey),
		...tasks.filter(({state}) => state === 'COMPLETED').map(({elementInstanceKey}) => elementInstanceKey),
	]);
	const completedCount = completedKeys.size;

	if (tasks.some(({state}) => state === 'CREATED')) {
		return {status: 'open', completedCount};
	}

	if (instances.some(({state, elementInstanceKey}) => state === 'ACTIVE' && !completedKeys.has(elementInstanceKey))) {
		return {status: 'active', completedCount};
	}

	return {status: completedCount > 0 ? 'completed' : 'notStarted', completedCount};
}

function getStageWork({
	model,
	currentStage,
	elementInstances,
	userTasks,
	rootVariables,
}: {
	model: CaseModel;
	currentStage: StageProgress | null;
	elementInstances: ElementInstance[];
	userTasks: UserTask[];
	rootVariables: Variable[];
}): StageWork {
	const openTasks = userTasks.filter(({state}) => state === 'CREATED');
	const stagesById = new Map(model.stages.map((stage) => [stage.id, stage]));
	const getOptionalStage = ({elementId}: UserTask) => {
		const stage = stagesById.get(model.stageIdByElementId.get(elementId) ?? '');
		return stage?.kind === 'optional' ? stage : undefined;
	};
	const optionalStageNames = Object.fromEntries(
		openTasks.flatMap((task) => {
			const optionalStage = getOptionalStage(task);
			return optionalStage === undefined ? [] : [[task.userTaskKey, optionalStage.name]];
		}),
	);
	const stage = currentStage?.stage;

	if (stage === undefined || currentStage === null) {
		return {activities: [], openTasks, optionalStageNames, completion: null};
	}

	const stageTasks = openTasks.filter((task) => {
		const stageId = model.stageIdByElementId.get(task.elementId);
		return stageId === undefined || stageId === stage.id || getOptionalStage(task) !== undefined;
	});
	// The element instance search returns UTC dates and the user task search dates with an offset, so the
	// run window compares timestamps, not strings.
	const runStart = Date.parse(currentStage.startDate ?? '') || 0;
	const stageInstances = elementInstances.filter(
		({elementId, startDate}) => stage.elementIds.includes(elementId) && Date.parse(startDate) >= runStart,
	);
	const openTasksByElementId = new Map(stageTasks.map((task) => [task.elementId, task]));
	const stageTaskHistory = userTasks.filter(
		({elementId, creationDate}) => stage.elementIds.includes(elementId) && Date.parse(creationDate) >= runStart,
	);
	const adHoc = stage.adHoc;
	const activityIds = adHoc === null ? [] : [...adHoc.requiredElementIds, ...adHoc.optionalElementIds];
	const activities = activityIds.map((elementId) => ({
		elementId,
		name: model.elementNames.get(elementId) ?? elementId,
		isRequired: adHoc?.requiredElementIds.includes(elementId) ?? false,
		...getActivityState(elementId, stageInstances, stageTaskHistory),
		userTask: openTasksByElementId.get(elementId) ?? null,
	}));
	const manualCompletion = stage.manualCompletion;
	const requiredActivities = activities.filter(({isRequired}) => isRequired);

	return {
		activities,
		openTasks: stageTasks.filter(({elementId}) => !activityIds.includes(elementId)),
		optionalStageNames,
		completion:
			manualCompletion === null
				? null
				: {
						messageName: manualCompletion.messageName,
						doneElementId: manualCompletion.doneElementId,
						correlationKey: resolveCorrelationKey(manualCompletion.correlationKey, rootVariables),
						isAllowed: requiredActivities.every(({completedCount}) => completedCount > 0),
					},
	};
}

export {getStageWork};
export type {ActivityStatus, StageActivity, StageCompletion, StageWork};
