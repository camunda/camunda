/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Section} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import type {RegisteredRouter} from '@tanstack/react-router';
import type {CurrentUser, UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {useHasRouteMatch} from '#/shared/useHasRouteMatch';
import {TurnOnNotificationPermission} from './TurnOnNotificationPermission';
import {TaskDetailsHeader} from './TaskDetailsHeader';
import {TabListNav, type TabItem} from './TabListNav';
import {Aside} from './Aside';
import layoutStyles from './taskDetailsLayoutCommon.module.scss';

type FileRouteTypes = RegisteredRouter['routeTree']['types']['fileRouteTypes'];
type TypeSafeTabItem = Omit<TabItem, 'to'> & {to: FileRouteTypes['to']};

type Props = {
	task: UserTask;
	currentUser: CurrentUser;
	assignButton: React.ReactNode;
	children: React.ReactNode;
};

const TaskDetailsLayout: React.FC<Props> = ({task, currentUser, assignButton, children}) => {
	const {t} = useTranslation();
	const hasRouteMatch = useHasRouteMatch();
	const tabs = [
		{
			key: 'task',
			title: t('tasklist.taskDetailsTaskTabLabel'),
			label: t('tasklist.taskDetailsShowTaskLabel'),
			selected: hasRouteMatch('/tasklist/$userTaskKey'),
			to: '/tasklist/$userTaskKey',
		},
		{
			key: 'process',
			title: t('tasklist.taskDetailsProcessTabLabel'),
			label: t('tasklist.taskDetailsShowBpmnProcessLabel'),
			selected: hasRouteMatch('/tasklist/$userTaskKey/process'),
			to: '/tasklist/$userTaskKey/process',
		},
		{
			key: 'history',
			title: t('tasklist.taskDetailsHistoryTabLabel'),
			label: t('tasklist.taskDetailsShowHistoryLabel'),
			selected: hasRouteMatch('/tasklist/$userTaskKey/history'),
			to: '/tasklist/$userTaskKey/history',
		},
	] satisfies TypeSafeTabItem[];

	return (
		<div className={layoutStyles.container} data-testid="details-info">
			<Section className={layoutStyles.content} level={4}>
				<TurnOnNotificationPermission />
				<TaskDetailsHeader
					taskName={task.name ?? task.elementId}
					processName={task.processName ?? task.processDefinitionId}
					assignee={task.assignee ?? null}
					taskState={task.state}
					user={currentUser}
					assignButton={assignButton}
				/>
				<TabListNav label={t('tasklist.taskDetailsNavLabel')} items={tabs} />
				{children}
			</Section>
			<Aside
				creationDate={task.creationDate}
				completionDate={task.completionDate}
				dueDate={task.dueDate}
				followUpDate={task.followUpDate}
				priority={task.priority}
				candidateUsers={task.candidateUsers}
				candidateGroups={task.candidateGroups}
				tenantId={task.tenantId}
				user={currentUser}
			/>
		</div>
	);
};

export {TaskDetailsLayout};
