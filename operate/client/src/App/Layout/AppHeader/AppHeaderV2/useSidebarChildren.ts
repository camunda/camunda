/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import {createPath} from 'react-router-dom';
import {
  Activity,
  GitBranch,
  LayoutDashboard,
  ListChecks,
  Table2,
  Workflow,
} from 'lucide-react';
import type {SidebarNode} from '@camunda/design-system';
import {Locations, Paths} from 'modules/Routes';
import {useCurrentPage} from 'modules/hooks/useCurrentPage';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {isForbidden} from 'modules/auth/isForbidden';

function locationToString(location: ReturnType<typeof Locations.processes>) {
  return typeof location === 'string' ? location : createPath(location);
}

function useSidebarChildren(hideNavLinks = false): SidebarNode[] {
  const {data: currentUser} = useCurrentUser();
  const {currentPage} = useCurrentPage();
  const forbidden = isForbidden(currentUser);
  const isOperationsPage =
    currentPage === 'batch-operations' || currentPage === 'operations-log';
  const [operationsExpansion, setOperationsExpansion] = useState({
    page: currentPage,
    expanded: isOperationsPage,
  });
  const operationsExpanded =
    operationsExpansion.page === currentPage
      ? operationsExpansion.expanded
      : isOperationsPage;

  return useMemo((): SidebarNode[] => {
    if (forbidden || hideNavLinks) {
      return [];
    }

    return [
      {
        type: 'item',
        key: 'dashboard',
        label: 'Dashboard',
        icon: LayoutDashboard,
        linkProps: {to: Paths.dashboard()},
      },
      {
        type: 'item',
        key: 'processes',
        label: 'Processes',
        icon: Workflow,
        isActive: (active) =>
          active === 'processes' || active.startsWith('process-details'),
        linkProps: {
          to: locationToString(Locations.processes()),
          state: {refreshContent: true, hideOptionalFilters: true},
        },
      },
      {
        type: 'item',
        key: 'decisions',
        label: 'Decisions',
        icon: GitBranch,
        isActive: (active) =>
          active === 'decisions' || active === 'decision-details',
        linkProps: {
          to: locationToString(Locations.decisions()),
          state: {refreshContent: true, hideOptionalFilters: true},
        },
      },
      {
        type: 'group',
        key: 'operations',
        label: 'Operations',
        icon: Activity,
        isExpanded: operationsExpanded,
        onToggleExpand: (expanded) => {
          setOperationsExpansion({page: currentPage, expanded});
        },
        children: [
          {
            type: 'item',
            key: 'batch-operations',
            label: 'Batch operations',
            icon: Table2,
            linkProps: {to: Paths.batchOperations()},
          },
          {
            type: 'item',
            key: 'operations-log',
            label: 'Operations log',
            icon: ListChecks,
            linkProps: {to: Paths.operationsLog()},
          },
        ],
      },
    ];
  }, [forbidden, hideNavLinks, currentPage, operationsExpanded]);
}

export {useSidebarChildren};
