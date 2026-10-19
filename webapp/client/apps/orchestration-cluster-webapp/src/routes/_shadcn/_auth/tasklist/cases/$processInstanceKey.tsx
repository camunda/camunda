/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import c4FormJsCss from '@bpmn-io/c4-theme/assets/form-js.css?url';
import c4TokensCss from '@bpmn-io/c4-theme/assets/tokens.css?url';
import formJsCss from '@bpmn-io/form-js-viewer/dist/assets/form-js.css?url';
import {createFileRoute, notFound, useNavigate, type ErrorComponentProps} from '@tanstack/react-router';
import {useQuery, useSuspenseQuery, type QueryClient} from '@tanstack/react-query';
import {parseCaseModel} from '@camunda/case-management-sdk';
import {PageLayout, toast} from '@camunda/design-system';
import type {Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import {TasklistCaseDetailsPage} from '#/tasklist/pages/TasklistCaseDetailsPage';
import {
	getCaseElementInstancesRequestBody,
	getCaseUserTasksRequestBody,
	getRootVariablesRequestBody,
	isTaskOfCase,
} from '#/tasklist/modules/case-details/getCaseRequestBodies';
import {caseDetailsSearchSchema} from '#/tasklist/modules/case-details/searchSchema';
import {useNextWork} from '#/tasklist/modules/case-details/useNextWork';
import {extractVariablesFromFormSchema} from '#/tasklist/modules/form-js/extractVariablesFromFormSchema';
import {getSelectedVariablesRequestBody} from '#/tasklist/modules/task-details-form/getSelectedVariablesRequestBody';
import {ForbiddenError, TruncatedVariableError} from '#/shared/errors';
import {queries} from '#/shared/http/queries';
import {requestErrorSchema} from '#/shared/http/request';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';
import {NotFoundPage} from '#/shared/pages/shadcn.components/NotFoundPage';

const REFETCH_INTERVAL = 5000;

type SelectedTaskData = {
	formSchema: string | null;
	variables: Variable[];
};

type LoaderData = {
	caseFormSchema: string | null;
	selectedTask: SelectedTaskData | null;
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

async function loadSelectedTask(queryClient: QueryClient, userTaskKey: string): Promise<SelectedTaskData | null> {
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

export const Route = createFileRoute('/_shadcn/_auth/tasklist/cases/$processInstanceKey')({
	validateSearch: caseDetailsSearchSchema,
	loaderDeps: ({search: {task}}) => ({task}),
	head: () => ({
		links: [
			{rel: 'stylesheet', href: formJsCss},
			{rel: 'stylesheet', href: c4TokensCss},
			{rel: 'stylesheet', href: c4FormJsCss},
		],
	}),
	loader: async ({context: {queryClient}, params: {processInstanceKey}, deps: {task}}): Promise<LoaderData> => {
		const processInstance = await returnNullIfNotFound(
			queryClient.query(queries.getProcessInstance(processInstanceKey)),
		);

		if (processInstance === null) {
			throw notFound();
		}

		const [xml, selectedTask] = await Promise.all([
			queryClient.query(queries.getProcessDefinitionXml(processInstance.processDefinitionKey)),
			task === undefined ? null : loadSelectedTask(queryClient, task),
			queryClient.query(queries.getCurrentUser()),
			queryClient.query(queries.queryElementInstances(getCaseElementInstancesRequestBody(processInstanceKey))),
			queryClient.query(queries.queryUserTasksPage(getCaseUserTasksRequestBody(processInstance))),
			queryClient.query(
				queries.queryVariables(getRootVariablesRequestBody(processInstanceKey), {truncateValues: false}),
			),
		]);
		const caseFormId = parseCaseModel(xml, processInstance.processDefinitionId).caseMarker?.caseFormId ?? null;
		const caseForm =
			caseFormId === null
				? null
				: await returnNullIfNotFound(
						queryClient.query(queries.getLatestFormByFormId({formId: caseFormId, tenantId: processInstance.tenantId})),
					);

		return {caseFormSchema: caseForm?.schema ?? null, selectedTask};
	},
	notFoundComponent: () => (
		<PageLayout>
			<NotFoundPage />
		</PageLayout>
	),
	errorComponent: function CaseDetailsErrorPage({error, reset}: ErrorComponentProps) {
		return (
			<PageLayout>
				{error instanceof ForbiddenError ? <ForbiddenPage /> : <GenericErrorPage reset={reset} />}
			</PageLayout>
		);
	},
	component: function TasklistCaseDetailsRoute() {
		const {t} = useTranslation();
		const navigate = useNavigate();
		const {processInstanceKey} = Route.useParams();
		const {task: selectedTaskKey} = Route.useSearch();
		const {caseFormSchema, selectedTask} = Route.useLoaderData();
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
			...queries.queryUserTasksPage(getCaseUserTasksRequestBody(processInstance)),
			refetchInterval: REFETCH_INTERVAL,
		});
		const {data: rootVariables} = useSuspenseQuery({
			...queries.queryVariables(getRootVariablesRequestBody(processInstanceKey), {truncateValues: false}),
			refetchInterval: REFETCH_INTERVAL,
		});
		const caseUserTasks = useMemo(
			() => userTasks.items.filter((userTask) => isTaskOfCase(userTask, processInstanceKey)),
			[userTasks.items, processInstanceKey],
		);
		const {data: task} = useQuery({
			...queries.getUserTask(selectedTaskKey ?? ''),
			enabled: selectedTaskKey !== undefined && selectedTask !== null,
		});

		const openTask = useCallback((userTaskKey: string) => navigate({to: '.', search: {task: userTaskKey}}), [navigate]);
		const closeTask = useCallback(() => navigate({to: '.', search: {}}), [navigate]);
		const showNoWork = useCallback(() => toast.info(t('tasklist.caseNoNextWork')), [t]);
		const {isSearchingNextWork, searchNextWork} = useNextWork({
			processInstance,
			username: currentUser.username,
			onNextTask: openTask,
			onNoWork: showNoWork,
		});
		const handleTaskCompleted = useCallback(
			(userTaskKey: string) => {
				searchNextWork(userTaskKey);
				closeTask();
			},
			[closeTask, searchNextWork],
		);

		return (
			<TasklistCaseDetailsPage
				processInstance={processInstance}
				xml={xml}
				elementInstances={elementInstances.items}
				userTasks={caseUserTasks}
				rootVariables={rootVariables.items}
				currentUser={currentUser}
				caseFormSchema={caseFormSchema}
				selectedTask={
					task === undefined || selectedTask === null || task.userTaskKey !== selectedTaskKey
						? null
						: {task, ...selectedTask}
				}
				isSearchingNextWork={isSearchingNextWork}
				onOpenTask={openTask}
				onCloseTask={closeTask}
				onTaskCompleted={handleTaskCompleted}
			/>
		);
	},
});
