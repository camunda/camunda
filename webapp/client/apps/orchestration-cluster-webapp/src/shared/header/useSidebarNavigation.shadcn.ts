/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMatchRoute, type RegisteredRouter} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';

import {
	Bot,
	Briefcase,
	ListChecks,
	ListTodo,
	Package,
	Settings,
	ShieldCheck,
	User,
	UserCog,
	Users,
	Waypoints,
	Workflow,
	Zap,
} from '@camunda/design-system/icons';
import {camundaAppIcons, type NavIcon, type SidebarNode} from '@camunda/design-system';
import type {AuthenticatedCurrentUser} from '@camunda/camunda-api-zod-schemas/8.11';
import {getAdminSectionConfig, isAdminSectionAvailable, type AdminSectionKey} from '#/admin/adminSections';
import {OPERATE_BASE} from '#/shared/operateBase';
import {hasComponentAccess} from '#/shared/componentAccess';
import {ENABLE_CASE_MANAGEMENT} from '#/shared/feature-flags';
import {useActiveComponentHomeRoute} from '#/shared/useActiveComponentHomeRoute';
import {getOperateSidebarItems} from './operateSidebarItems';

type FileRouteTypes = RegisteredRouter['routeTree']['types']['fileRouteTypes'];
const tabRoutes = {
	tasklistIndex: '/tasklist',
	tasklistProcesses: '/tasklist/processes',
	tasklistCases: '/tasklist/cases',
	operateDashboard: OPERATE_BASE,
	adminIndex: '/admin',
	adminUsers: '/admin/users',
	adminGroups: '/admin/groups',
	adminRoles: '/admin/roles',
	adminTenants: '/admin/tenants',
	adminMappingRules: '/admin/mapping-rules',
	adminAuthorizations: '/admin/authorizations',
	adminGlobalTaskListeners: '/admin/global-task-listeners',
	adminClusterVariables: '/admin/cluster-variables',
	adminMcpProcesses: '/admin/mcp-processes',
	adminOperationsLog: '/admin/operations-log',
} as const satisfies Record<string, FileRouteTypes['to']>;

type SidebarNavigation = {
	ariaLabel: string;
	homeRoute: FileRouteTypes['to'];
	items: SidebarNode[];
	product?: {
		icon: NavIcon;
		label: string;
	};
};

type AdminNavItem = {
	key: AdminSectionKey;
	to: FileRouteTypes['to'];
	labelKey: string;
	icon: NavIcon;
};

const ADMIN_NAV_ITEMS: AdminNavItem[] = [
	{
		key: 'users',
		to: tabRoutes['adminUsers'],
		labelKey: 'admin.headerNavItemUsers',
		icon: User,
	},
	{
		key: 'mapping-rules',
		to: tabRoutes['adminMappingRules'],
		labelKey: 'admin.headerNavItemMappingRules',
		icon: Waypoints,
	},
	{
		key: 'groups',
		to: tabRoutes['adminGroups'],
		labelKey: 'admin.headerNavItemGroups',
		icon: Users,
	},
	{
		key: 'roles',
		to: tabRoutes['adminRoles'],
		labelKey: 'admin.headerNavItemRoles',
		icon: UserCog,
	},
	{
		key: 'tenants',
		to: tabRoutes['adminTenants'],
		labelKey: 'admin.headerNavItemTenants',
		icon: Package,
	},
	{
		key: 'authorizations',
		to: tabRoutes['adminAuthorizations'],
		labelKey: 'admin.headerNavItemAuthorizations',
		icon: ShieldCheck,
	},
	{
		key: 'global-task-listeners',
		to: tabRoutes['adminGlobalTaskListeners'],
		labelKey: 'admin.headerNavItemGlobalTaskListeners',
		icon: Zap,
	},
	{
		key: 'cluster-variables',
		to: tabRoutes['adminClusterVariables'],
		labelKey: 'admin.headerNavItemClusterVariables',
		icon: Settings,
	},
	{
		key: 'mcp-processes',
		to: tabRoutes['adminMcpProcesses'],
		labelKey: 'admin.headerNavItemMcpProcesses',
		icon: Bot,
	},
	{
		key: 'operations-log',
		to: tabRoutes['adminOperationsLog'],
		labelKey: 'admin.headerNavItemOperationsLog',
		icon: ListChecks,
	},
];

function useSidebarNavigation(currentUser: AuthenticatedCurrentUser): SidebarNavigation {
	const {t} = useTranslation();
	const matchRoute = useMatchRoute();
	const {authorizedComponents} = currentUser;
	const isProcessesRoute = matchRoute({to: tabRoutes['tasklistProcesses'], fuzzy: true}) !== false;
	const isCasesRoute = ENABLE_CASE_MANAGEMENT && matchRoute({to: tabRoutes['tasklistCases'], fuzzy: true}) !== false;
	const activeComponentHomeRoute = useActiveComponentHomeRoute();
	const isTasklistRoute = activeComponentHomeRoute === tabRoutes['tasklistIndex'];
	const isAdminRoute = activeComponentHomeRoute === tabRoutes['adminIndex'];

	if (isTasklistRoute) {
		const hasTasklistAccess = hasComponentAccess('tasklist', authorizedComponents);

		return {
			ariaLabel: t('tasklist.taskPanelNavAria'),
			homeRoute: activeComponentHomeRoute,
			product: {
				icon: camundaAppIcons.tasklist,
				label: 'Tasklist',
			},
			items: hasTasklistAccess
				? [
						{
							type: 'item',
							key: 'tasks',
							label: t('tasklist.headerNavItemTasks'),
							icon: ListTodo,
							isActive: !isProcessesRoute && !isCasesRoute,
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
						...(ENABLE_CASE_MANAGEMENT
							? [
									{
										type: 'item',
										key: 'cases',
										label: t('tasklist.headerNavItemCases'),
										icon: Briefcase,
										isActive: isCasesRoute,
										linkProps: {
											to: tabRoutes['tasklistCases'],
										},
									} satisfies SidebarNode,
								]
							: []),
					]
				: [],
		};
	}

	const isOperateRoute = activeComponentHomeRoute === tabRoutes['operateDashboard'];

	if (isOperateRoute) {
		const hasOperateAccess = hasComponentAccess('operate', authorizedComponents);

		return {
			ariaLabel: t('headerAppBarLabel'),
			homeRoute: activeComponentHomeRoute,
			product: {
				icon: camundaAppIcons.operate,
				label: 'Operate',
			},
			items: hasOperateAccess ? getOperateSidebarItems({t, matchRoute, base: OPERATE_BASE}) : [],
		};
	}

	if (isAdminRoute) {
		const hasAdminAccess = hasComponentAccess('admin', authorizedComponents);
		const sectionConfig = getAdminSectionConfig();

		return {
			ariaLabel: t('admin.adminPanelNavAria'),
			homeRoute: activeComponentHomeRoute,
			product: {
				icon: camundaAppIcons.admin,
				label: 'Admin',
			},
			items: hasAdminAccess
				? ADMIN_NAV_ITEMS.filter(({key}) => isAdminSectionAvailable(key, sectionConfig)).map(
						({key, to, labelKey, icon}): SidebarNode => ({
							type: 'item',
							key,
							label: t(labelKey),
							icon,
							isActive: matchRoute({to, fuzzy: true}) !== false,
							linkProps: {to},
						}),
					)
				: [],
		};
	}

	return {
		ariaLabel: t('headerAppBarLabel'),
		homeRoute: tabRoutes['tasklistIndex'],
		items: [],
	};
}

export {ADMIN_NAV_ITEMS, useSidebarNavigation};
