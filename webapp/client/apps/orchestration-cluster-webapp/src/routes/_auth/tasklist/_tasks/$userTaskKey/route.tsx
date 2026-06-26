/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {t} from 'i18next';
import {createFileRoute, notFound, Outlet, useNavigate} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {queries} from '#/shared/http/queries';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {TaskDetailPage} from '#/tasklist/pages/TaskDetailPage';
import {DetailsSkeleton} from '#/tasklist/modules/task-details/components/DetailsSkeleton';
import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {requestErrorSchema} from '#/shared/http/request';

const POLLING_STATES: UserTask['state'][] = ['CANCELING', 'UPDATING', 'COMPLETING', 'ASSIGNING'];

export const Route = createFileRoute('/_auth/tasklist/_tasks/$userTaskKey')({
	loader: async ({context: {queryClient}, params: {userTaskKey}}) => {
		try {
			await queryClient.ensureQueryData(queries.getUserTask(userTaskKey));
		} catch (error) {
			const result = requestErrorSchema.safeParse(error);

			if (result.success && result.data.response?.status === 404) {
				throw notFound({routeId: '/_auth/tasklist/_tasks/$userTaskKey'});
			}

			throw error;
		}
	},
	pendingComponent: () => <DetailsSkeleton data-testid="details-skeleton" />,
	component: function TaskDetailRoute() {
		const {userTaskKey} = Route.useParams();
		const navigate = useNavigate();

		const {data: task, refetch} = useSuspenseQuery({
			...queries.getUserTask(userTaskKey),
			refetchInterval(query) {
				const state = query.state.data?.state;
				if (state && POLLING_STATES.includes(state)) {
					return 5000;
				}
				return false;
			},
		});

		const {data: currentUser} = useSuspenseQuery(queries.getCurrentUser());

		useEffect(() => {
			if (task.state === 'CANCELED') {
				notificationsStore.displayNotification({
					kind: 'info',
					title: t('tasklist.processInstanceCancelledNotification'),
					subtitle: `${task.processName ?? task.processDefinitionId} (${task.processInstanceKey})`,
					isDismissable: true,
				});
				navigate({to: '/tasklist'});
			}
		}, [navigate, task.processInstanceKey, task.processName, task.state, task.processDefinitionId]);

		return (
			<TaskDetailPage task={task} currentUser={currentUser} refetch={refetch}>
				<Outlet />
			</TaskDetailPage>
		);
	},
});
