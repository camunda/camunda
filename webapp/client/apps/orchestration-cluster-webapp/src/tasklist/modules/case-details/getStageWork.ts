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
	status: ActivityStatus;
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
	/** Open user tasks of the stage that are not an activity of the list above. */
	openTasks: UserTask[];
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

function getActivityStatus(
	elementId: string,
	stageInstances: ElementInstance[],
	openTasksByElementId: Map<string, UserTask>,
): ActivityStatus {
	if (openTasksByElementId.has(elementId)) {
		return 'open';
	}

	const instances = stageInstances.filter((instance) => instance.elementId === elementId);

	if (instances.some(({state}) => state === 'ACTIVE')) {
		return 'active';
	}

	return instances.some(({state}) => state === 'COMPLETED') ? 'completed' : 'notStarted';
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
	const stage = currentStage?.stage;

	if (stage === undefined || currentStage === null) {
		return {activities: [], openTasks, completion: null};
	}

	const stageTasks = openTasks.filter(({elementId}) => {
		const stageId = model.stageIdByElementId.get(elementId);
		return stageId === undefined || stageId === stage.id;
	});
	const stageStartDate = currentStage.startDate ?? '';
	const stageInstances = elementInstances.filter(
		({elementId, startDate}) => stage.elementIds.includes(elementId) && startDate >= stageStartDate,
	);
	const openTasksByElementId = new Map(stageTasks.map((task) => [task.elementId, task]));
	const adHoc = stage.adHoc;
	const activityIds = adHoc === null ? [] : [...adHoc.requiredElementIds, ...adHoc.optionalElementIds];
	const activities = activityIds.map((elementId) => ({
		elementId,
		name: model.elementNames.get(elementId) ?? elementId,
		isRequired: adHoc?.requiredElementIds.includes(elementId) ?? false,
		status: getActivityStatus(elementId, stageInstances, openTasksByElementId),
		userTask: openTasksByElementId.get(elementId) ?? null,
	}));
	const manualCompletion = stage.manualCompletion;
	const requiredActivities = activities.filter(({isRequired}) => isRequired);

	return {
		activities,
		openTasks: stageTasks.filter(({elementId}) => !activityIds.includes(elementId)),
		completion:
			manualCompletion === null
				? null
				: {
						messageName: manualCompletion.messageName,
						doneElementId: manualCompletion.doneElementId,
						correlationKey: resolveCorrelationKey(manualCompletion.correlationKey, rootVariables),
						isAllowed: requiredActivities.every(({status}) => status === 'completed'),
					},
	};
}

export {getStageWork};
export type {ActivityStatus, StageActivity, StageCompletion, StageWork};
