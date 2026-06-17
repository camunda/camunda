/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMatchRoute, type RegisteredRouter} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import type {C3NavigationAppProps, C3NavigationNavBarElement} from '@camunda/camunda-composite-components';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {tracking} from '#/shared/tracking';
import {useCallback} from 'react';

type NavbarConfig = {
	app: C3NavigationAppProps;
	elements: C3NavigationNavBarElement[];
};

function isAuthorized(componentName: string, authorizedComponents: string[]): boolean {
	return authorizedComponents.includes(componentName) || authorizedComponents.includes('*');
}

type FileRouteTypes = RegisteredRouter['routeTree']['types']['fileRouteTypes'];
const tabRoutes = {
	tasklistIndex: '/tasklist',
	tasklistProcesses: '/tasklist/processes',
	admin: '/admin',
	operate: '/operate',
	operateProcesses: '/operate/processes',
	operateDecisions: '/operate/decisions',
	operateOperationsLog: '/operate/operations-log',
	operateBatchOperations: '/operate/batch-operations',
} as const satisfies Record<string, FileRouteTypes['to']>;

function useNavbar(currentUser: CurrentUser): NavbarConfig {
	const {t} = useTranslation();
	const matchRoute = useMatchRoute();
	const {authorizedComponents} = currentUser;

	const hasRouteMatch = useCallback(
		(...routes: FileRouteTypes['to'][]) => routes.some((to) => matchRoute({to}) !== false),
		[matchRoute],
	);
	const hasComponentRouteMatch = useCallback(
		(component: 'tasklist' | 'admin' | 'operate') => matchRoute({to: `/${component}`, fuzzy: true}) !== false,
		[matchRoute],
	);

	if (hasComponentRouteMatch('tasklist')) {
		const hasTasklistAccess = isAuthorized('tasklist', authorizedComponents);

		return {
			app: {
				ariaLabel: 'Camunda Tasklist',
				name: 'Tasklist',
				routeProps: {
					to: tabRoutes['tasklistIndex'],
					onClick: () => {
						tracking.track({eventName: 'tasklist:navigation', link: 'header-logo'});
					},
				},
			},
			elements: hasTasklistAccess
				? [
						{
							key: 'tasks',
							label: t('tasklist.headerNavItemTasks'),
							isCurrentPage: !hasRouteMatch('/tasklist/processes') && hasRouteMatch('/tasklist', '/tasklist/$id'),
							routeProps: {
								to: tabRoutes['tasklistIndex'],
								onClick: () => {
									tracking.track({
										eventName: 'tasklist:navigation',
										link: 'header-tasks',
									});
								},
								activeOptions: {
									exact: true,
								},
							},
						},
						{
							key: 'processes',
							label: t('tasklist.headerNavItemProcesses'),
							isCurrentPage: hasRouteMatch('/tasklist/processes'),
							routeProps: {
								to: tabRoutes['tasklistProcesses'],
								onClick: () => {
									tracking.track({
										eventName: 'tasklist:navigation',
										link: 'header-processes',
									});
								},
							},
						},
					]
				: [],
		};
	}

	if (hasComponentRouteMatch('admin')) {
		const hasAdminAccess = isAuthorized('admin', authorizedComponents);

		return {
			app: {
				ariaLabel: 'Camunda Admin',
				name: 'Admin',
				routeProps: {
					to: tabRoutes['admin'],
					onClick: () => {
						tracking.track({eventName: 'admin:navigation', link: 'header-logo'});
					},
				},
			},
			elements: hasAdminAccess ? [] : [],
		};
	}

	if (hasComponentRouteMatch('operate')) {
		const hasOperateAccess = isAuthorized('operate', authorizedComponents);

		return {
			app: {
				ariaLabel: 'Camunda Operate',
				name: 'Operate',
				routeProps: {
					to: tabRoutes['operate'],
					onClick: () => {
						tracking.track({eventName: 'operate:navigation', link: 'header-logo'});
					},
				},
			},
			elements: hasOperateAccess
				? [
						{
							key: 'dashboard',
							label: t('operate.dashboard.title'),
							isCurrentPage: hasRouteMatch('/operate'),
							routeProps: {
								to: tabRoutes['operate'],
								activeOptions: {exact: true},
								onClick: () => {
									tracking.track({eventName: 'operate:navigation', link: 'header-dashboard'});
								},
							},
						},
						{
							key: 'processes',
							label: t('operate.processes.title'),
							isCurrentPage: hasRouteMatch('/operate/processes'),
							routeProps: {
								to: tabRoutes['operateProcesses'],
								onClick: () => {
									tracking.track({eventName: 'operate:navigation', link: 'header-processes'});
								},
							},
						},
						{
							key: 'decisions',
							label: t('operate.decisions.title'),
							isCurrentPage: hasRouteMatch('/operate/decisions'),
							routeProps: {
								to: tabRoutes['operateDecisions'],
								onClick: () => {
									tracking.track({eventName: 'operate:navigation', link: 'header-decisions'});
								},
							},
						},
						{
							key: 'operations-log',
							label: t('operate.operationsLog.title'),
							isCurrentPage: hasRouteMatch('/operate/operations-log'),
							routeProps: {
								to: tabRoutes['operateOperationsLog'],
								onClick: () => {
									tracking.track({eventName: 'operate:navigation', link: 'header-operations-log'});
								},
							},
						},
						{
							key: 'batch-operations',
							label: t('operate.batchOperations.title'),
							isCurrentPage: hasRouteMatch('/operate/batch-operations'),
							routeProps: {
								to: tabRoutes['operateBatchOperations'],
								onClick: () => {
									tracking.track({eventName: 'operate:navigation', link: 'header-batch-operations'});
								},
							},
						},
					]
				: [],
		};
	}

	return {
		app: {
			ariaLabel: 'Camunda',
			name: 'Camunda',
			routeProps: {
				to: '/',
			},
		},
		elements: [],
	};
}

export {useNavbar};
