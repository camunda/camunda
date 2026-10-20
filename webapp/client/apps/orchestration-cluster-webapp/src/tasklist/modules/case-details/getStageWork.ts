/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getVariableReference, type CaseModel, type StageProgress} from '@camunda/case-management-sdk';
import type {ElementInstance, UserTask, Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import {tryParseJSON} from '#/tasklist/modules/json/tryParseJSON';

type TaskStatus = 'open' | 'active' | 'completed' | 'notStarted';

type StageTask = {
	/** Unique in the list: the element ID of an ad-hoc task, the user task key of any other open task. */
	key: string;
	elementId: string;
	name: string;
	isRequired: boolean;
	/** An optional task of an ad-hoc stage: the case worker starts it through the activation API. */
	canStart: boolean;
	/** A task of the ad-hoc stage's list, which counts towards "x of y done". */
	isAdHocTask: boolean;
	/** The current state: an open task or a running instance wins over earlier completions. */
	status: TaskStatus;
	/** How often the task completed in this run of the stage, also while it runs again. */
	completedCount: number;
	/** The open user task, if there is one. */
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
	/** The tasks of the stage: required first, then optional. */
	tasks: StageTask[];
	isAdHoc: boolean;
	completion: StageCompletion | null;
};

// prototype: the engine evaluates the correlation key; the case page only resolves a plain variable reference
// such as `=caseId`, or a static key.
function resolveCorrelationKey(expression: string | null, caseVariables: Variable[]): string | null {
	if (expression === null) {
		return null;
	}

	if (!expression.startsWith('=')) {
		return expression;
	}

	const variableName = getVariableReference(expression);
	const variable = caseVariables.find(({name}) => name === variableName);
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
): Pick<StageTask, 'status' | 'completedCount'> {
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
	caseVariables,
}: {
	model: CaseModel;
	currentStage: StageProgress | null;
	elementInstances: ElementInstance[];
	userTasks: UserTask[];
	caseVariables: Variable[];
}): StageWork {
	const openTasks = userTasks.filter(({state}) => state === 'CREATED');
	const toOpenTask = (userTask: UserTask): StageTask => ({
		key: userTask.userTaskKey,
		elementId: userTask.elementId,
		name: userTask.name || userTask.elementId,
		isRequired: true,
		canStart: false,
		isAdHocTask: false,
		status: 'open',
		completedCount: 0,
		userTask,
	});
	const stage = currentStage?.stage;

	if (stage === undefined || currentStage === null) {
		return {tasks: openTasks.map(toOpenTask), isAdHoc: false, completion: null};
	}

	const stageTasks = openTasks.filter((task) => {
		const stageId = model.stageIdByElementId.get(task.elementId);
		return stageId === undefined || stageId === stage.id;
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
	const toAdHocTask = (elementId: string, isRequired: boolean): StageTask => ({
		key: elementId,
		elementId,
		name: model.elementNames.get(elementId) ?? elementId,
		isRequired,
		canStart: !isRequired,
		isAdHocTask: true,
		...getActivityState(elementId, stageInstances, stageTaskHistory),
		userTask: openTasksByElementId.get(elementId) ?? null,
	});
	const requiredTasks = (adHoc?.requiredElementIds ?? []).map((elementId) => toAdHocTask(elementId, true));
	const optionalTasks = (adHoc?.optionalElementIds ?? []).map((elementId) => toAdHocTask(elementId, false));
	const adHocElementIds = new Set([...requiredTasks, ...optionalTasks].map(({elementId}) => elementId));
	// Any other open task of the stage: the tasks of a sequential stage, or the tasks inside an activated
	// sub-process of an ad-hoc stage.
	const otherOpenTasks = stageTasks.filter(({elementId}) => !adHocElementIds.has(elementId)).map(toOpenTask);
	const manualCompletion = stage.manualCompletion;

	return {
		tasks: [...requiredTasks, ...otherOpenTasks, ...optionalTasks],
		isAdHoc: adHoc !== null,
		completion:
			manualCompletion === null
				? null
				: {
						messageName: manualCompletion.messageName,
						doneElementId: manualCompletion.doneElementId,
						correlationKey: resolveCorrelationKey(manualCompletion.correlationKey, caseVariables),
						isAllowed: requiredTasks.every(({completedCount}) => completedCount > 0),
					},
	};
}

export {getStageWork};
export type {StageCompletion, StageTask, StageWork, TaskStatus};
