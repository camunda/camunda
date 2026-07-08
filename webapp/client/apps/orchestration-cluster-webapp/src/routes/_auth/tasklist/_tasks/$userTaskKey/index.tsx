/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createFileRoute, useSearch} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {queries} from '#/shared/http/queries';
import {TaskDetailsTaskPage} from '#/tasklist/pages/TaskDetailsTaskPage';
import {TaskDetailsTaskErrorPage} from '#/tasklist/pages/TaskDetailsTaskErrorPage';
import {extractVariablesFromFormSchema} from '#/tasklist/modules/form-js/extractVariablesFromFormSchema';
import {getSelectedVariablesRequestBody} from '#/tasklist/modules/task-details-form/getSelectedVariablesRequestBody';
import {TruncatedVariableError} from '#/shared/errors';
import type {Variable} from '@camunda/camunda-api-zod-schemas/8.10';

type LoaderData = {
	formSchema: string | null;
	variables: Variable[];
};

export const Route = createFileRoute('/_auth/tasklist/_tasks/$userTaskKey/')({
	loader: async ({context: {queryClient}, params: {userTaskKey}}): Promise<LoaderData> => {
		const task = await queryClient.ensureQueryData(queries.getUserTask(userTaskKey));

		if (task.formKey === null) {
			return {formSchema: null, variables: []};
		}

		const form = await queryClient.ensureQueryData(queries.getUserTaskForm(userTaskKey));
		const variableNames = extractVariablesFromFormSchema(form.schema);

		if (variableNames.length === 0) {
			return {formSchema: form.schema, variables: []};
		}

		const variablesResponse = await queryClient.ensureQueryData(
			queries.queryVariablesByUserTask(userTaskKey, getSelectedVariablesRequestBody(variableNames), {
				truncateValues: false,
			}),
		);

		if (variablesResponse.items.some((variable) => variable.isTruncated)) {
			throw new TruncatedVariableError();
		}

		return {formSchema: form.schema, variables: variablesResponse.items};
	},
	errorComponent: TaskDetailsTaskErrorPage,
	component: function TaskTabRoute() {
		const {userTaskKey} = Route.useParams();
		const search = useSearch({from: '/_auth/tasklist/_tasks'});
		const {formSchema, variables} = Route.useLoaderData();
		const {data: task} = useSuspenseQuery(queries.getUserTask(userTaskKey));
		const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());

		return (
			<TaskDetailsTaskPage
				task={task}
				currentUser={currentUser}
				search={search}
				formSchema={formSchema}
				variables={variables}
			/>
		);
	},
	remountDeps: ({params: {userTaskKey}}) => [userTaskKey],
});
