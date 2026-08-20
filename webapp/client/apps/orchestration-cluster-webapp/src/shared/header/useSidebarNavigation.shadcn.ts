/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMatchRoute, type RegisteredRouter} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {ListTodo, Workflow} from 'lucide-react';
import type {SidebarNode} from '@camunda/design-system';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {hasComponentAccess} from '#/shared/componentAccess';
import {useHasRouteMatch} from '#/shared/useHasRouteMatch';

type FileRouteTypes = RegisteredRouter['routeTree']['types']['fileRouteTypes'];
const tabRoutes = {
	tasklistIndex: '/shadcn/tasklist',
	tasklistProcesses: '/shadcn/tasklist/processes',
} as const satisfies Record<string, FileRouteTypes['to']>;

type SidebarNavigation = {
	ariaLabel: string;
	homeRoute: FileRouteTypes['to'];
	items: SidebarNode[];
};

function useSidebarNavigation(currentUser: CurrentUser): SidebarNavigation {
	const {t} = useTranslation();
	const matchRoute = useMatchRoute();
	const hasRouteMatch = useHasRouteMatch();
	const {authorizedComponents} = currentUser;
	const isProcessesRoute = matchRoute({to: tabRoutes['tasklistProcesses'], fuzzy: true}) !== false;

	if (matchRoute({to: tabRoutes['tasklistIndex'], fuzzy: true}) !== false) {
		const hasTasklistAccess = hasComponentAccess('tasklist', authorizedComponents);

		return {
			ariaLabel: t('tasklist.taskPanelNavAria'),
			homeRoute: tabRoutes['tasklistIndex'],
			items: hasTasklistAccess
				? [
						{
							type: 'item',
							key: 'tasks',
							label: t('tasklist.headerNavItemTasks'),
							icon: ListTodo,
							isActive: !isProcessesRoute && hasRouteMatch('/shadcn/tasklist'),
							linkProps: {
								to: tabRoutes['tasklistIndex'],
								activeOptions: {
									exact: true,
								},
							},
						},
						{
							type: 'item',
							key: 'processes',
							label: t('tasklist.headerNavItemProcesses'),
							icon: Workflow,
							isActive: isProcessesRoute,
							linkProps: {
								to: tabRoutes['tasklistProcesses'],
							},
						},
					]
				: [],
		};
	}

	return {
		ariaLabel: t('headerAppBarLabel'),
		homeRoute: tabRoutes['tasklistIndex'],
		items: [],
	};
}

export {useSidebarNavigation};
