/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CaseModel} from '@camunda/case-management-sdk';
import type {
	AuditLog,
	AuditLogActorType,
	AuditLogEntityType,
	AuditLogOperationType,
	ElementInstance,
	UserTask,
} from '@camunda/camunda-api-zod-schemas/8.11';

type CaseActivityKind =
	| 'caseStarted'
	| 'caseCancelled'
	| 'caseSuspended'
	| 'caseResumed'
	| 'caseModified'
	| 'caseMigrated'
	| 'taskAssigned'
	| 'taskUnassigned'
	| 'taskUpdated'
	| 'taskCompleted'
	| 'taskDraftSaved'
	| 'dataChanged'
	| 'documentsChanged'
	| 'incidentResolved'
	| 'stepCompleted';

type CaseActivity = {
	/** The audit log key of the first entry of the activity. */
	key: string;
	kind: CaseActivityKind;
	/** The task or step that the operation is about, for operations below the case. */
	elementName: string | null;
	/** The stage that contains the task, step or variable scope. */
	stageName: string | null;
	/** The new assignee, for an assignment. */
	assignee: string | null;
	/** The variables that the operation set. The audit log has the names, never the values. */
	variableNames: string[];
	actorId: string | null;
	actorType: AuditLogActorType | null;
	timestamp: string;
};

const KIND_BY_OPERATION: Partial<Record<AuditLogEntityType, Partial<Record<AuditLogOperationType, CaseActivityKind>>>> =
	{
		PROCESS_INSTANCE: {
			CREATE: 'caseStarted',
			CANCEL: 'caseCancelled',
			SUSPEND: 'caseSuspended',
			RESUME: 'caseResumed',
			MODIFY: 'caseModified',
			MIGRATE: 'caseMigrated',
		},
		USER_TASK: {
			ASSIGN: 'taskAssigned',
			UNASSIGN: 'taskUnassigned',
			UPDATE: 'taskUpdated',
			COMPLETE: 'taskCompleted',
		},
		VARIABLE: {
			CREATE: 'dataChanged',
			UPDATE: 'dataChanged',
		},
		INCIDENT: {
			RESOLVE: 'incidentResolved',
		},
		JOB: {
			COMPLETE: 'stepCompleted',
		},
	};

type Params = {
	/** Newest first. */
	auditLogs: AuditLog[];
	model: CaseModel;
	elementInstances: ElementInstance[];
	userTasks: UserTask[];
	/** The variables that hold document references. */
	documentVariableNames: Set<string>;
};

/**
 * One command writes all its records with the same timestamp, so the entries of one actor at one instant
 * belong to one operation, for example the variables that a task completion sets.
 */
function getCommandKey({actorId, timestamp}: AuditLog) {
	return `${actorId ?? ''}|${new Date(timestamp).getTime()}`;
}

/** Turns the audit log of a case into the activities that a case worker relates to, newest first. */
function getCaseActivity({auditLogs, model, elementInstances, userTasks, documentVariableNames}: Params) {
	const elementInstanceByKey = new Map(elementInstances.map((instance) => [instance.elementInstanceKey, instance]));
	const userTaskByKey = new Map(userTasks.map((task) => [task.userTaskKey, task]));
	const stageNameById = new Map(model.stages.map(({id, name}) => [id, name]));

	function getElementName(elementId: string, name: string | null | undefined) {
		return name || model.elementNames.get(elementId) || elementId;
	}

	function getStageName(elementId: string | null) {
		if (elementId === null) {
			return null;
		}

		const stageId = stageNameById.has(elementId) ? elementId : model.stageIdByElementId.get(elementId);
		return stageId === undefined ? null : (stageNameById.get(stageId) ?? null);
	}

	function getElement({userTaskKey, elementInstanceKey}: AuditLog) {
		const task = userTaskKey === null ? undefined : userTaskByKey.get(userTaskKey);

		if (task !== undefined) {
			return {elementId: task.elementId, name: getElementName(task.elementId, task.name)};
		}

		const instance = elementInstanceKey === null ? undefined : elementInstanceByKey.get(elementInstanceKey);

		if (instance === undefined || instance.type === 'PROCESS') {
			return null;
		}

		return {elementId: instance.elementId, name: getElementName(instance.elementId, instance.elementName)};
	}

	const completedTaskCommands = new Set(
		auditLogs
			.filter(({entityType, operationType}) => entityType === 'USER_TASK' && operationType === 'COMPLETE')
			.map(getCommandKey),
	);
	const variableNamesByCommand = new Map<string, Set<string>>();

	for (const log of auditLogs) {
		if (log.entityType === 'VARIABLE' && log.entityDescription !== null) {
			const key = getCommandKey(log);
			variableNamesByCommand.set(key, (variableNamesByCommand.get(key) ?? new Set()).add(log.entityDescription));
		}
	}

	const activities: CaseActivity[] = [];
	const variableActivityByGroup = new Map<string, CaseActivity>();

	for (const log of auditLogs) {
		const kind = KIND_BY_OPERATION[log.entityType]?.[log.operationType];

		if (kind === undefined) {
			continue;
		}

		const commandKey = getCommandKey(log);

		if (log.entityType === 'VARIABLE') {
			// The task completion lists the variables that it set.
			if (completedTaskCommands.has(commandKey) || log.entityDescription === null) {
				continue;
			}

			const groupKey = `${commandKey}|${log.elementInstanceKey ?? ''}`;
			const activity = variableActivityByGroup.get(groupKey);

			if (activity !== undefined) {
				activity.variableNames = [...new Set([...activity.variableNames, log.entityDescription])].sort();
				if (activity.kind === 'documentsChanged' && !documentVariableNames.has(log.entityDescription)) {
					activity.kind = 'dataChanged';
				}
				continue;
			}

			const scope = log.elementInstanceKey === null ? undefined : elementInstanceByKey.get(log.elementInstanceKey);
			const isTaskScope = scope?.type === 'USER_TASK';
			const newActivity: CaseActivity = {
				key: log.auditLogKey,
				kind: isTaskScope
					? 'taskDraftSaved'
					: documentVariableNames.has(log.entityDescription)
						? 'documentsChanged'
						: 'dataChanged',
				elementName: isTaskScope ? getElementName(scope.elementId, scope.elementName) : null,
				stageName: getStageName(scope?.elementId ?? null),
				assignee: null,
				variableNames: [log.entityDescription],
				actorId: log.actorId,
				actorType: log.actorType,
				timestamp: log.timestamp,
			};

			variableActivityByGroup.set(groupKey, newActivity);
			activities.push(newActivity);
			continue;
		}

		const isCaseOperation = log.entityType === 'PROCESS_INSTANCE';
		const element = isCaseOperation ? null : getElement(log);

		activities.push({
			key: log.auditLogKey,
			kind,
			elementName: element?.name ?? (log.entityType === 'JOB' ? log.entityDescription : null),
			stageName: getStageName(element?.elementId ?? null),
			assignee: kind === 'taskAssigned' ? log.relatedEntityKey : null,
			variableNames: kind === 'taskCompleted' ? [...(variableNamesByCommand.get(commandKey) ?? [])].sort() : [],
			actorId: log.actorId,
			actorType: log.actorType,
			timestamp: log.timestamp,
		});
	}

	return activities;
}

export {getCaseActivity};
export type {CaseActivity, CaseActivityKind};
