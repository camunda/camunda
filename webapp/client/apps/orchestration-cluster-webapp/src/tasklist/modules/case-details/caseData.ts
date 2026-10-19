/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSuspenseQuery, type QueryClient} from '@tanstack/react-query';
import {getCaseVariableNames, parseCaseModel} from '@camunda/case-management-sdk';
import type {
	CurrentUser,
	ElementInstance,
	ProcessInstance,
	UserTask,
	Variable,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {TruncatedVariableError} from '#/shared/errors';
import {queries} from '#/shared/http/queries';
import {requestErrorSchema} from '#/shared/http/request';
import {
	getCaseElementInstancesRequestBody,
	getCaseUserTasksRequestBody,
	getCaseVariablesRequestBody,
	getDocumentVariablesRequestBody,
} from '#/tasklist/modules/case-details/getCaseRequestBodies';
import {extractVariablesFromFormSchema} from '#/tasklist/modules/form-js/extractVariablesFromFormSchema';
import {getSelectedVariablesRequestBody} from '#/tasklist/modules/task-details-form/getSelectedVariablesRequestBody';

const REFETCH_INTERVAL = 5000;

type CaseData = {
	currentUser: CurrentUser;
	processInstance: ProcessInstance;
	xml: string;
	elementInstances: ElementInstance[];
	userTasks: UserTask[];
	/** The variables that the BPMN and the case form name, see getCaseVariableNames. */
	caseVariables: Variable[];
	/** The root-scope variables that hold document references. */
	documentVariables: Variable[];
};

type CaseRouteData = {
	caseFormSchema: string | null;
	variableNames: string[];
};

type CaseTaskData = {
	formSchema: string | null;
	variables: Variable[];
};

function isNotFoundError(error: unknown): boolean {
	const result = requestErrorSchema.safeParse(error);
	return result.success && result.data.response?.status === 404;
}

async function returnNullIfNotFound<T>(promise: Promise<T>): Promise<T | null> {
	try {
		return await promise;
	} catch (error) {
		if (isNotFoundError(error)) {
			return null;
		}

		throw error;
	}
}

/**
 * Prefetches the data that every route of a case reads, and derives the variable names to request from
 * the BPMN and the case form. Returns `null` when the case does not exist.
 */
async function loadCaseData(queryClient: QueryClient, processInstanceKey: string): Promise<CaseRouteData | null> {
	const processInstance = await returnNullIfNotFound(queryClient.query(queries.getProcessInstance(processInstanceKey)));

	if (processInstance === null) {
		return null;
	}

	const xml = await queryClient.query(queries.getProcessDefinitionXml(processInstance.processDefinitionKey));
	const model = parseCaseModel(xml, processInstance.processDefinitionId);
	const caseFormSchema = await loadCaseForm(
		queryClient,
		model.caseMarker?.caseFormId ?? null,
		processInstance.tenantId,
	);
	const variableNames = [
		...new Set([
			...getCaseVariableNames(model),
			...(caseFormSchema === null ? [] : extractVariablesFromFormSchema(caseFormSchema)),
		]),
	];

	await Promise.all([
		queryClient.query(queries.getCurrentUser()),
		queryClient.query(queries.queryElementInstances(getCaseElementInstancesRequestBody(processInstanceKey))),
		queryClient.query(queries.queryUserTasksPage(getCaseUserTasksRequestBody(processInstanceKey))),
		queryClient.query(
			queries.queryVariables(getCaseVariablesRequestBody(processInstanceKey, variableNames), {truncateValues: false}),
		),
		queryClient.query(
			queries.queryVariables(getDocumentVariablesRequestBody(processInstanceKey), {truncateValues: false}),
		),
	]);

	return {caseFormSchema, variableNames};
}

/** Reads the case data that the loader prefetched, and keeps it fresh. */
function useCaseData(processInstanceKey: string, variableNames: string[]): CaseData {
	const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());
	const {data: processInstance} = useSuspenseQuery({
		...queries.getProcessInstance(processInstanceKey),
		refetchInterval: REFETCH_INTERVAL,
	});
	const {data: xml} = useSuspenseQuery(queries.getProcessDefinitionXml(processInstance.processDefinitionKey));
	const {data: elementInstances} = useSuspenseQuery({
		...queries.queryElementInstances(getCaseElementInstancesRequestBody(processInstanceKey)),
		refetchInterval: REFETCH_INTERVAL,
	});
	const {data: userTasks} = useSuspenseQuery({
		...queries.queryUserTasksPage(getCaseUserTasksRequestBody(processInstanceKey)),
		refetchInterval: REFETCH_INTERVAL,
	});
	const {data: caseVariables} = useSuspenseQuery({
		...queries.queryVariables(getCaseVariablesRequestBody(processInstanceKey, variableNames), {
			truncateValues: false,
		}),
		refetchInterval: REFETCH_INTERVAL,
	});
	const {data: documentVariables} = useSuspenseQuery({
		...queries.queryVariables(getDocumentVariablesRequestBody(processInstanceKey), {truncateValues: false}),
		refetchInterval: REFETCH_INTERVAL,
	});

	return {
		currentUser,
		processInstance,
		xml,
		elementInstances: elementInstances.items,
		userTasks: userTasks.items,
		caseVariables: caseVariables.items,
		documentVariables: documentVariables.items,
	};
}

/** Loads the form of a task and its variables. Returns `null` when the task does not exist. */
async function loadCaseTask(queryClient: QueryClient, userTaskKey: string): Promise<CaseTaskData | null> {
	const task = await returnNullIfNotFound(queryClient.query(queries.getUserTask(userTaskKey)));

	if (task === null) {
		return null;
	}

	if (task.formKey === null || task.formKey === undefined) {
		return {formSchema: null, variables: []};
	}

	const form = await queryClient.query(queries.getUserTaskForm(userTaskKey));
	const variableNames = extractVariablesFromFormSchema(form.schema);

	if (variableNames.length === 0) {
		return {formSchema: form.schema, variables: []};
	}

	// prototype: NEXT_WORK.md reads these variables from primary storage; that option does not exist yet.
	const {items} = await queryClient.query(
		queries.queryVariablesByUserTask(userTaskKey, getSelectedVariablesRequestBody(variableNames), {
			truncateValues: false,
		}),
	);

	if (items.some(({isTruncated}) => isTruncated)) {
		throw new TruncatedVariableError();
	}

	return {formSchema: form.schema, variables: items};
}

/** Loads the case form that the case marker names. Returns `null` without a case form. */
async function loadCaseForm(
	queryClient: QueryClient,
	caseFormId: string | null,
	tenantId: string,
): Promise<string | null> {
	if (caseFormId === null) {
		return null;
	}

	const form = await returnNullIfNotFound(
		queryClient.query(queries.getLatestFormByFormId({formId: caseFormId, tenantId})),
	);

	return form?.schema ?? null;
}

export {loadCaseData, loadCaseTask, useCaseData};
export type {CaseData};
