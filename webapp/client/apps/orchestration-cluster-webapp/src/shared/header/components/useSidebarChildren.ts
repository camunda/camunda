/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Activity, GitBranch, LayoutDashboard, ListChecks, Table2, Workflow} from '@camunda/design-system/icons';
import type {SidebarNode} from '@camunda/design-system';
import type {AuthenticatedCurrentUser} from '@camunda/camunda-api-zod-schemas/8.11';
import {hasComponentAccess} from '#/shared/componentAccess';
import {useCurrentPage} from './useCurrentPage';

function useSidebarChildren(currentUser: AuthenticatedCurrentUser): SidebarNode[] {
	const {t} = useTranslation();
	const {currentPage} = useCurrentPage();
	const forbidden = !hasComponentAccess('operate', currentUser.authorizedComponents);
	const isOperationsPage = currentPage === 'batch-operations' || currentPage === 'operations-log';
	const [operationsExpansion, setOperationsExpansion] = useState({
		page: currentPage,
		expanded: isOperationsPage,
	});
	const operationsExpanded = operationsExpansion.page === currentPage ? operationsExpansion.expanded : isOperationsPage;

	return useMemo((): SidebarNode[] => {
		if (forbidden) {
			return [];
		}

		return [
			{
				type: 'item',
				key: 'dashboard',
				label: t('operate.dashboard.title'),
				icon: LayoutDashboard,
				linkProps: {to: '/operate'},
			},
			{
				type: 'item',
				key: 'processes',
				label: t('operate.processes.title'),
				icon: Workflow,
				isActive: (active) => active === 'processes' || active.startsWith('process-details'),
				linkProps: {
					to: '/operate/processes',
					search: {active: true, suspended: true, incidents: true},
					state: {operateHideOptionalFilters: true},
				},
			},
			{
				type: 'item',
				key: 'decisions',
				label: t('operate.decisions.title'),
				icon: GitBranch,
				isActive: (active) => active === 'decisions' || active === 'decision-details',
				linkProps: {
					to: '/operate/decisions',
					search: {evaluated: true, failed: true},
					state: {operateHideOptionalFilters: true},
				},
			},
			{
				type: 'group',
				key: 'operations',
				label: t('operate.operations.title'),
				icon: Activity,
				isExpanded: operationsExpanded,
				onToggleExpand: (expanded) => {
					setOperationsExpansion({page: currentPage, expanded});
				},
				children: [
					{
						type: 'item',
						key: 'batch-operations',
						label: t('operate.batchOperations.title'),
						icon: Table2,
						linkProps: {to: '/operate/batch-operations'},
					},
					{
						type: 'item',
						key: 'operations-log',
						label: t('operate.operationsLog.title'),
						icon: ListChecks,
						linkProps: {to: '/operate/operations-log'},
					},
				],
			},
		];
	}, [forbidden, currentPage, operationsExpanded, t]);
}

export {useSidebarChildren};
