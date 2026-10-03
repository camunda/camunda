/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';
import type {ElementInstance, ElementInstanceInspection, UserTask} from '@camunda/camunda-api-zod-schemas/8.11';
import {
	compareWaitStateLabels,
	getWaitStateLabel,
	type WaitStateKind,
} from '#/tasklist/modules/cases/getWaitStateLabel';

type CaseWait = {
	id: string;
	kind: WaitStateKind;
	kindLabel: string;
	title: string;
	assignment: string | null;
	since: string | null;
	extras: string[];
};

function getUserTaskAssignment(userTask: UserTask, currentUsername: string): string {
	if (userTask.assignee === currentUsername) {
		return t('tasklist.caseDetailsAssignedToYou');
	}

	if (userTask.assignee !== null) {
		return userTask.assignee;
	}

	const candidates = [...userTask.candidateGroups, ...userTask.candidateUsers];

	return candidates.length === 0 ? t('tasklist.caseDetailsUnassigned') : candidates.join(', ');
}

function getCaseWaits({
	waitStates,
	userTasks,
	elementInstances,
	elementNames,
	currentUsername,
}: {
	waitStates: ElementInstanceInspection[];
	userTasks: UserTask[];
	elementInstances: ElementInstance[];
	elementNames: Map<string, string>;
	currentUsername: string;
}): CaseWait[] {
	const userTasksByKey = new Map(userTasks.map((userTask) => [userTask.userTaskKey, userTask]));
	const elementInstancesByKey = new Map(elementInstances.map((instance) => [instance.elementInstanceKey, instance]));

	return waitStates
		.map((waitState) => ({waitState, label: getWaitStateLabel(waitState)}))
		.sort((first, second) => compareWaitStateLabels(first.label, second.label))
		.map(({waitState, label}) => {
			const {details} = waitState;
			const userTask = details.waitStateType === 'USER_TASK' ? userTasksByKey.get(details.taskKey) : undefined;
			const isRootWait = waitState.processInstanceKey === waitState.rootProcessInstanceKey;
			const elementName = isRootWait ? elementNames.get(waitState.elementId) : undefined;

			return {
				id: label.id,
				kind: label.kind,
				kindLabel: label.label,
				title: userTask?.name || elementName || label.detail,
				assignment: userTask === undefined ? null : getUserTaskAssignment(userTask, currentUsername),
				since: userTask?.creationDate ?? elementInstancesByKey.get(waitState.elementInstanceKey)?.startDate ?? null,
				extras: userTask === undefined ? label.extras : label.extras.filter((extra) => extra !== label.detail),
			};
		});
}

export {getCaseWaits, getUserTaskAssignment};
export type {CaseWait};
