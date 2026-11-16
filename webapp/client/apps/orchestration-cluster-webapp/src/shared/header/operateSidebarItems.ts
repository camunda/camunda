/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {RegisteredRouter, useMatchRoute} from '@tanstack/react-router';
import type {TFunction} from 'i18next';
import {GitBranch, History, LayoutDashboard, Layers, Settings2, Workflow} from '@camunda/design-system/icons';
import type {SidebarNode} from '@camunda/design-system';

type RouteTo = RegisteredRouter['routeTree']['types']['fileRouteTypes']['to'];

type OperationsGroupState = {
	isExpanded: boolean;
	onToggleExpand: (isExpanded: boolean) => void;
};

type Options = {
	t: TFunction;
	matchRoute: ReturnType<typeof useMatchRoute>;
	base: '/operate' | '/operate-preview';
	operationsGroup?: OperationsGroupState;
};

function getOperateSidebarItems({t, matchRoute, base, operationsGroup}: Options): SidebarNode[] {
	const dashboard = base as RouteTo;
	const processes = `${base}/processes` as RouteTo;
	const decisions = `${base}/decisions` as RouteTo;
	const operationsLog = `${base}/operations-log` as RouteTo;
	const batchOperations = `${base}/batch-operations` as RouteTo;
	const isDashboardRoute = matchRoute({to: dashboard, fuzzy: false}) !== false;
	const isProcessesRoute = matchRoute({to: processes, fuzzy: true}) !== false;
	const isDecisionsRoute = matchRoute({to: decisions, fuzzy: true}) !== false;
	const isOperationsLogRoute = matchRoute({to: operationsLog, fuzzy: true}) !== false;
	const isBatchOperationsRoute = matchRoute({to: batchOperations, fuzzy: true}) !== false;

	return [
		{
			type: 'item',
			key: 'dashboard',
			label: t('operate.dashboard.title'),
			icon: LayoutDashboard,
			isActive: isDashboardRoute,
			linkProps: {to: dashboard, activeOptions: {exact: true}},
		},
		{
			type: 'item',
			key: 'processes',
			label: t('operate.processes.title'),
			icon: Workflow,
			isActive: isProcessesRoute,
			linkProps: {
				to: processes,
				search: {active: true, suspended: true, incidents: true},
				state: {operateHideOptionalFilters: true},
			},
		},
		{
			type: 'item',
			key: 'decisions',
			label: t('operate.decisions.title'),
			icon: GitBranch,
			isActive: isDecisionsRoute,
			linkProps: {
				to: decisions,
				search: {evaluated: true, failed: true},
				state: {operateHideOptionalFilters: true},
			},
		},
		{
			type: 'group',
			key: 'operations',
			label: t('operate.operations.title'),
			icon: Settings2,
			...operationsGroup,
			children: [
				{
					type: 'item',
					key: 'batch-operations',
					label: t('operate.batchOperations.title'),
					icon: Layers,
					isActive: isBatchOperationsRoute,
					linkProps: {to: batchOperations},
				},
				{
					type: 'item',
					key: 'operations-log',
					label: t('operate.operationsLog.title'),
					icon: History,
					isActive: isOperationsLogRoute,
					linkProps: {to: operationsLog},
				},
			],
		},
	];
}

export {getOperateSidebarItems};
