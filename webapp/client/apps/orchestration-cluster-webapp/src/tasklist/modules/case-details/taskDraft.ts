/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryClient} from '@tanstack/react-query';
import isEqual from 'lodash/isEqual';
import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {queries} from '#/shared/http/queries';
import {request} from '#/shared/http/request';
import {waitUntilReady} from '#/shared/http/waitUntilReady';
import {tryParseJSON} from '#/tasklist/modules/json/tryParseJSON';
import {getSelectedVariablesRequestBody} from '#/tasklist/modules/task-details-form/getSelectedVariablesRequestBody';

type Task = Pick<UserTask, 'userTaskKey' | 'elementInstanceKey'>;

/** Sets the variables in the local scope of the user task, so that they do not reach the case before completion. */
async function setTaskLocalVariables(task: Task, variables: Record<string, unknown>) {
	const {error} = await request(
		endpoints.updateElementInstanceVariables(task.elementInstanceKey, {variables, local: true}),
	);

	if (error !== null) {
		throw error;
	}
}

/**
 * Saves the draft of a task form, and waits until the search returns it, so that reopening the task shows it.
 * Returns `false` when the draft is saved, but the search does not return it yet.
 */
async function saveTaskDraft(queryClient: QueryClient, task: Task, variables: Record<string, unknown>) {
	await setTaskLocalVariables(task, variables);

	const names = Object.keys(variables);
	const result = await waitUntilReady(
		queryClient,
		queries.queryVariablesByUserTask(task.userTaskKey, getSelectedVariablesRequestBody(names), {
			truncateValues: false,
		}),
		{
			isReady: ({items}) =>
				names.every((name) =>
					items.some(
						(item) =>
							item.name === name &&
							item.scopeKey === task.elementInstanceKey &&
							isEqual(tryParseJSON(item.value), variables[name]),
					),
				),
		},
	);

	return result !== undefined;
}

/**
 * The engine merges the completion variables of a task without output mappings starting at the task's own scope.
 * So a variable that a draft set there keeps a changed value locally, and the value is lost when the task completes.
 * An unchanged value propagates (camunda/camunda#55491), so before completion the draft variables are set to the
 * completion values.
 */
async function syncTaskDraft(task: Task, draftVariableNames: Set<string>, variables: Record<string, unknown>) {
	const draftVariables = Object.fromEntries(Object.entries(variables).filter(([name]) => draftVariableNames.has(name)));

	if (Object.keys(draftVariables).length > 0) {
		await setTaskLocalVariables(task, draftVariables);
	}
}

export {saveTaskDraft, syncTaskDraft};
