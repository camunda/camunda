/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Variable} from '@camunda/camunda-api-zod-schemas/8.10';
import {useInfiniteQuery, useSuspenseQuery} from '@tanstack/react-query';
import {createFileRoute, useSearch} from '@tanstack/react-router';
import {useCallback, useMemo} from 'react';
import {TruncatedVariableError} from '#/shared/errors';
import {queries} from '#/shared/http/queries';
import {extractVariablesFromFormSchema} from '#/tasklist/modules/form-js/extractVariablesFromFormSchema';
import {getSelectedVariablesRequestBody} from '#/tasklist/modules/task-details-form/getSelectedVariablesRequestBody';
import {TaskDetailsTaskPage} from '#/tasklist/pages/shadcn.components/TaskDetailsTaskPage';
import {TaskDetailsTaskErrorPage} from '#/tasklist/pages/shadcn.components/TaskDetailsTaskErrorPage';

type LoaderData = {
	formSchema: string | null;
	variables: Variable[];
};

export const Route = createFileRoute('/shadcn/_auth/tasklist/_tasks/$userTaskKey/')({
	loader: async ({context: {queryClient}, params: {userTaskKey}}): Promise<LoaderData> => {
		const task = await queryClient.query(queries.getUserTask(userTaskKey));

		if (task.formKey === null) {
			await queryClient.infiniteQuery(queries.queryAllVariablesByUserTask(userTaskKey));
			return {formSchema: null, variables: []};
		}

		const form = await queryClient.query(queries.getUserTaskForm(userTaskKey));
		const variableNames = extractVariablesFromFormSchema(form.schema);

		if (variableNames.length === 0) {
			return {formSchema: form.schema, variables: []};
		}

		const variablesResponse = await queryClient.query(
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
		const search = useSearch({from: '/shadcn/_auth/tasklist/_tasks'});
		const {formSchema, variables} = Route.useLoaderData();
		const {data: task} = useSuspenseQuery(queries.getUserTask(userTaskKey));
		const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());
		const isVariablesView = formSchema === null;
		const {
			data: variablesData,
			fetchNextPage,
			hasNextPage,
			isFetchingNextPage,
			isFetchNextPageError,
		} = useInfiniteQuery({
			...queries.queryAllVariablesByUserTask(userTaskKey),
			enabled: isVariablesView,
			refetchInterval: isVariablesView && task.assignee === null ? 5000 : undefined,
			refetchOnWindowFocus: isVariablesView && task.assignee === null,
			refetchOnReconnect: isVariablesView && task.assignee === null,
		});
		const allVariables = useMemo(() => variablesData?.pages.flatMap((page) => page.items) ?? [], [variablesData]);
		const totalVariables = Math.max(variablesData?.pages.at(-1)?.page.totalItems ?? 0, allVariables.length);
		const onLoadNextVariablesPage = useCallback(() => {
			if (hasNextPage && !isFetchingNextPage) {
				void fetchNextPage();
			}
		}, [fetchNextPage, hasNextPage, isFetchingNextPage]);

		return (
			<TaskDetailsTaskPage
				task={task}
				currentUser={currentUser}
				search={search}
				formSchema={formSchema}
				variables={isVariablesView ? allVariables : variables}
				totalVariables={totalVariables}
				hasNextVariablesPage={hasNextPage}
				isFetchingNextVariablesPage={isFetchingNextPage}
				isNextVariablesPageError={isFetchNextPageError}
				onLoadNextVariablesPage={onLoadNextVariablesPage}
			/>
		);
	},
	remountDeps: ({params: {userTaskKey}}) => [userTaskKey],
});
