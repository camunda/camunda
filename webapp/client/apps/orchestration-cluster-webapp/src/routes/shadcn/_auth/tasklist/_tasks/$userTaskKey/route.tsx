/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {toast} from '@camunda/design-system';
import {useSuspenseQuery} from '@tanstack/react-query';
import {createFileRoute, type ErrorComponentProps, notFound, Outlet, redirect} from '@tanstack/react-router';
import {t} from 'i18next';
import {ForbiddenError} from '#/shared/errors';
import {queries} from '#/shared/http/queries';
import {requestErrorSchema} from '#/shared/http/request';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';
import {DetailsSkeleton} from '#/tasklist/modules/task-details/shadcn.components/DetailsSkeleton';
import {AssignButton} from '#/tasklist/modules/task-details/shadcn.components/AssignButton';
import {TaskDetailPage} from '#/tasklist/pages/shadcn.components/TaskDetailPage';
import {NotFoundPage} from '#/shared/pages/shadcn.components/NotFoundPage';

const POLLING_STATES: UserTask['state'][] = ['CANCELING', 'UPDATING', 'COMPLETING', 'ASSIGNING'];

export const Route = createFileRoute('/shadcn/_auth/tasklist/_tasks/$userTaskKey')({
	loader: async ({context: {queryClient}, params: {userTaskKey}}) => {
		try {
			const task = await queryClient.query(queries.getUserTask(userTaskKey));
			if (task.state === 'CANCELED') {
				toast.info(t('tasklist.processInstanceCancelledNotification'), {
					description: `${task.processName ?? task.processDefinitionId} (${task.processInstanceKey})`,
				});
				throw redirect({to: '/shadcn/tasklist'});
			}
		} catch (error) {
			const result = requestErrorSchema.safeParse(error);

			if (result.success && result.data.response?.status === 404) {
				throw notFound({routeId: '/shadcn/_auth/tasklist/_tasks/$userTaskKey'});
			}

			throw error;
		}
	},
	pendingComponent: () => <DetailsSkeleton data-testid="details-skeleton" />,
	notFoundComponent: NotFoundPage,
	errorComponent: function TaskDetailErrorPage({error, reset}: ErrorComponentProps) {
		if (error instanceof ForbiddenError) {
			return <ForbiddenPage />;
		}

		return <GenericErrorPage reset={reset} />;
	},
	component: function TaskDetailRoute() {
		const {userTaskKey} = Route.useParams();
		const {data: task} = useSuspenseQuery({
			...queries.getUserTask(userTaskKey),
			refetchInterval(query) {
				const state = query.state.data?.state;

				return state && POLLING_STATES.includes(state) ? 5000 : false;
			},
		});
		const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());

		return (
			<TaskDetailPage
				task={task}
				currentUser={currentUser}
				assignButton={
					<AssignButton
						key={task.userTaskKey}
						userTaskKey={task.userTaskKey}
						taskState={task.state}
						assignee={task.assignee ?? null}
						currentUser={currentUser.username}
					/>
				}
			>
				<Outlet />
			</TaskDetailPage>
		);
	},
});
