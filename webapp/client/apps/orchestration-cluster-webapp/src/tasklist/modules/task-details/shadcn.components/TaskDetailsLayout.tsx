/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {CurrentUser, UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {useTranslation} from 'react-i18next';
import {useHasRouteMatch} from '#/shared/useHasRouteMatch';
import {Aside} from './Aside';
import {TabListNav, type TabItem} from './TabListNav';
import {TaskDetailsHeader} from './TaskDetailsHeader';
import {useMemo} from 'react';

type Props = {
	task: UserTask;
	currentUser: CurrentUser;
	assignButton: React.ReactNode;
	children: React.ReactNode;
};

const TaskDetailsLayout: React.FC<Props> = ({task, currentUser, assignButton, children}) => {
	const {t} = useTranslation();
	const hasRouteMatch = useHasRouteMatch();
	const tabs = useMemo<TabItem[]>(
		() => [
			{
				key: 'task',
				title: t('tasklist.taskDetailsTaskTabLabel'),
				label: t('tasklist.taskDetailsShowTaskLabel'),
				selected: hasRouteMatch('/shadcn/tasklist/$userTaskKey'),
				to: '/shadcn/tasklist/$userTaskKey',
			},
			{
				key: 'process',
				title: t('tasklist.taskDetailsProcessTabLabel'),
				label: t('tasklist.taskDetailsShowBpmnProcessLabel'),
				selected: hasRouteMatch('/shadcn/tasklist/$userTaskKey/process'),
				to: '/shadcn/tasklist/$userTaskKey/process',
			},
			{
				key: 'history',
				title: t('tasklist.taskDetailsHistoryTabLabel'),
				label: t('tasklist.taskDetailsShowHistoryLabel'),
				selected: hasRouteMatch('/shadcn/tasklist/$userTaskKey/history'),
				to: '/shadcn/tasklist/$userTaskKey/history',
			},
		],
		[t, hasRouteMatch],
	);

	return (
		<div className="grid h-full w-full grid-cols-[minmax(0,1fr)_19.5rem] overflow-hidden" data-testid="details-info">
			<section className="flex min-h-0 flex-col items-center gap-2 overflow-y-auto pt-4">
				<TaskDetailsHeader
					taskName={task.name ?? task.elementId}
					processName={task.processName ?? task.processDefinitionId}
					assignee={task.assignee ?? null}
					taskState={task.state}
					user={currentUser}
					assignButton={assignButton}
				/>
				<TabListNav label={t('tasklist.taskDetailsNavLabel')} items={tabs} userTaskKey={task.userTaskKey}>
					{children}
				</TabListNav>
			</section>
			<div className="min-h-0 border-l border-border pt-4">
				<Aside
					creationDate={task.creationDate}
					completionDate={task.completionDate}
					dueDate={task.dueDate}
					followUpDate={task.followUpDate}
					priority={task.priority}
					candidateUsers={task.candidateUsers}
					candidateGroups={task.candidateGroups}
					tenantId={task.tenantId}
					businessId={task.businessId}
					user={currentUser}
				/>
			</div>
		</div>
	);
};

export {TaskDetailsLayout};
