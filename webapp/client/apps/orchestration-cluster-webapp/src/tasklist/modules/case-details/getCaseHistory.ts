/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';
import type {ElementInstance, Incident, UserTask} from '@camunda/camunda-api-zod-schemas/8.11';

type CaseHistoryKind = 'opened' | 'closed' | 'step' | 'task' | 'incident';

type CaseHistoryEntry = {
	id: string;
	kind: CaseHistoryKind;
	title: string;
	description: string | null;
	timestamp: string;
};

const IGNORED_ELEMENT_TYPES = new Set<ElementInstance['type']>([
	'PROCESS',
	'SEQUENCE_FLOW',
	'USER_TASK',
	'EXCLUSIVE_GATEWAY',
	'PARALLEL_GATEWAY',
	'INCLUSIVE_GATEWAY',
	'EVENT_BASED_GATEWAY',
	'MULTI_INSTANCE_BODY',
	'UNSPECIFIED',
	'UNKNOWN',
]);

function getElementEntry(elementInstance: ElementInstance, name: string): CaseHistoryEntry | null {
	const id = `element-${elementInstance.elementInstanceKey}`;

	if (elementInstance.type === 'START_EVENT') {
		return {
			id,
			kind: 'opened',
			title: t('tasklist.caseDetailsHistoryOpened'),
			description: name,
			timestamp: elementInstance.startDate,
		};
	}

	if (elementInstance.type === 'END_EVENT') {
		return {
			id,
			kind: 'closed',
			title: t('tasklist.caseDetailsHistoryClosed'),
			description: name,
			timestamp: elementInstance.endDate ?? elementInstance.startDate,
		};
	}

	if (IGNORED_ELEMENT_TYPES.has(elementInstance.type)) {
		return null;
	}

	switch (elementInstance.state) {
		case 'ACTIVE':
			return {
				id,
				kind: 'step',
				title: t('tasklist.caseDetailsHistoryStepStarted', {name}),
				description: null,
				timestamp: elementInstance.startDate,
			};
		case 'COMPLETED':
			return {
				id,
				kind: 'step',
				title: t('tasklist.caseDetailsHistoryStepCompleted', {name}),
				description: null,
				timestamp: elementInstance.endDate ?? elementInstance.startDate,
			};
		case 'TERMINATED':
			return {
				id,
				kind: 'step',
				title: t('tasklist.caseDetailsHistoryStepCanceled', {name}),
				description: null,
				timestamp: elementInstance.endDate ?? elementInstance.startDate,
			};
	}
}

function getUserTaskEntry(userTask: UserTask): CaseHistoryEntry {
	const name = userTask.name || userTask.elementId;
	const description =
		userTask.assignee === null ? null : t('tasklist.caseDetailsHistoryBy', {actor: userTask.assignee});

	if (userTask.state === 'COMPLETED') {
		return {
			id: `task-${userTask.userTaskKey}`,
			kind: 'task',
			title: t('tasklist.caseDetailsHistoryTaskCompleted', {name}),
			description,
			timestamp: userTask.completionDate ?? userTask.creationDate,
		};
	}

	if (userTask.state === 'CANCELED') {
		return {
			id: `task-${userTask.userTaskKey}`,
			kind: 'task',
			title: t('tasklist.caseDetailsHistoryTaskCanceled', {name}),
			description,
			timestamp: userTask.completionDate ?? userTask.creationDate,
		};
	}

	return {
		id: `task-${userTask.userTaskKey}`,
		kind: 'task',
		title: t('tasklist.caseDetailsHistoryTaskCreated', {name}),
		description,
		timestamp: userTask.creationDate,
	};
}

function getCaseHistory({
	elementInstances,
	userTasks,
	incidents,
	elementNames,
}: {
	elementInstances: ElementInstance[];
	userTasks: UserTask[];
	incidents: Incident[];
	elementNames: Map<string, string>;
}): CaseHistoryEntry[] {
	const getName = (elementId: string, elementName?: string | null) =>
		elementName || elementNames.get(elementId) || elementId;
	const elementEntries = elementInstances.flatMap(
		(elementInstance) =>
			getElementEntry(elementInstance, getName(elementInstance.elementId, elementInstance.elementName)) ?? [],
	);
	const incidentEntries = incidents.map((incident): CaseHistoryEntry => ({
		id: `incident-${incident.incidentKey}`,
		kind: 'incident',
		title: t('tasklist.caseDetailsHistoryIncident', {name: getName(incident.elementId)}),
		description: incident.errorMessage,
		timestamp: incident.creationTime,
	}));

	return [...elementEntries, ...userTasks.map(getUserTaskEntry), ...incidentEntries].sort(
		(first, second) => Date.parse(second.timestamp) - Date.parse(first.timestamp),
	);
}

export {getCaseHistory};
export type {CaseHistoryEntry, CaseHistoryKind};
