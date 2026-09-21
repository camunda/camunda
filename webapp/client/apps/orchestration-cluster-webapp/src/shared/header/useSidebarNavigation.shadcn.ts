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
	GitBranch,
	History,
	LayoutDashboard,
	Layers,
	ListChecks,
	ListTodo,
	Package,
	Settings,
	Settings2,
	ShieldCheck,
	User,
	UserCog,
	Users,
	Waypoints,
	Workflow,
	Zap,
} from '@camunda/design-system/icons';
import {camundaAppIcons, type NavIcon, type SidebarNode} from '@camunda/design-system';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {getAdminSectionConfig, isAdminSectionAvailable, type AdminSectionKey} from '#/admin/adminSections';
import {hasComponentAccess} from '#/shared/componentAccess';
import {useActiveComponentHomeRoute} from '#/shared/useActiveComponentHomeRoute';

type FileRouteTypes = RegisteredRouter['routeTree']['types']['fileRouteTypes'];
const tabRoutes = {
	tasklistIndex: '/tasklist',
	tasklistProcesses: '/tasklist/processes',
	// The Dashboard is the only Operate page migrated to the Design System so far, so it
	// points at the migration-time /operate-preview leaf. Every other Operate item below
	// still points at its live Carbon route — those pages haven't moved yet.
	operateDashboard: '/operate-preview',
	operateProcesses: '/operate/processes',
	operateDecisions: '/operate/decisions',
	operateOperationsLog: '/operate/operations-log',
	operateBatchOperations: '/operate/batch-operations',
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

function useSidebarNavigation(currentUser: CurrentUser): SidebarNavigation {
	const {t} = useTranslation();
	const matchRoute = useMatchRoute();
	const {authorizedComponents} = currentUser;
	const isProcessesRoute = matchRoute({to: tabRoutes['tasklistProcesses'], fuzzy: true}) !== false;
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
							isActive: !isProcessesRoute,
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

	const isOperateRoute = activeComponentHomeRoute === tabRoutes['operateDashboard'];

	if (isOperateRoute) {
		const hasOperateAccess = hasComponentAccess('operate', authorizedComponents);
		const isOperateProcessesRoute = matchRoute({to: tabRoutes['operateProcesses'], fuzzy: true}) !== false;
		const isOperateDecisionsRoute = matchRoute({to: tabRoutes['operateDecisions'], fuzzy: true}) !== false;
		const isOperateOperationsLogRoute = matchRoute({to: tabRoutes['operateOperationsLog'], fuzzy: true}) !== false;
		const isOperateBatchOperationsRoute = matchRoute({to: tabRoutes['operateBatchOperations'], fuzzy: true}) !== false;

		return {
			ariaLabel: t('headerAppBarLabel'),
			homeRoute: activeComponentHomeRoute,
			product: {
				icon: camundaAppIcons.operate,
				label: 'Operate',
			},
			items: hasOperateAccess
				? [
						{
							type: 'item',
							key: 'dashboard',
							label: t('operate.dashboard.title'),
							icon: LayoutDashboard,
							isActive: !isOperateProcessesRoute && !isOperateDecisionsRoute,
							linkProps: {
								to: tabRoutes['operateDashboard'],
								activeOptions: {exact: true},
							},
						},
						{
							type: 'item',
							key: 'processes',
							label: t('operate.processes.title'),
							icon: Workflow,
							isActive: isOperateProcessesRoute,
							linkProps: {
								to: tabRoutes['operateProcesses'],
							},
						},
						{
							type: 'item',
							key: 'decisions',
							label: t('operate.decisions.title'),
							icon: GitBranch,
							isActive: isOperateDecisionsRoute,
							linkProps: {
								to: tabRoutes['operateDecisions'],
								search: {evaluated: true, failed: true},
							},
						},
						{
							type: 'group',
							key: 'operations',
							label: t('operate.operations.title'),
							icon: Settings2,
							children: [
								{
									type: 'item',
									key: 'batch-operations',
									label: t('operate.batchOperations.title'),
									icon: Layers,
									isActive: isOperateBatchOperationsRoute,
									linkProps: {
										to: tabRoutes['operateBatchOperations'],
									},
								},
								{
									type: 'item',
									key: 'operations-log',
									label: t('operate.operationsLog.title'),
									icon: History,
									isActive: isOperateOperationsLogRoute,
									linkProps: {
										to: tabRoutes['operateOperationsLog'],
									},
								},
							],
						},
					]
				: [],
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

export {useSidebarNavigation};
