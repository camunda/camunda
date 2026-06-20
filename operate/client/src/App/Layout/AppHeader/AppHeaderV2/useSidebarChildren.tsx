/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {
  Activity,
  Dashboard,
  DataTable,
  DecisionTree,
  Flow,
  ListChecked,
} from '@carbon/react/icons';
import type {SidebarNodeDescriptor} from '@camunda/camunda-composite-components';
import {Locations, Paths} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {useCurrentPage} from 'modules/hooks/useCurrentPage';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {isForbidden} from 'modules/auth/isForbidden';

function useSidebarChildren(): SidebarNodeDescriptor[] {
  const {data: currentUser} = useCurrentUser();
  const {currentPage} = useCurrentPage();
  const forbidden = isForbidden(currentUser);

  // @ts-expect-error - we need to fix it from the C3 side
  return useMemo(() => {
    if (forbidden) {
      return [];
    }

    const children = [
      {
        type: 'item',
        key: 'dashboard',
        label: 'Dashboard',
        icon: Dashboard,
        linkProps: {to: Paths.dashboard()},
        onClick: () => {
          tracking.track({
            eventName: 'navigation',
            link: 'header-dashboard',
            currentPage,
          });
        },
      },
      {
        type: 'item',
        key: 'processes',
        label: 'Processes',
        icon: Flow,
        isActive: (active: string) =>
          active === 'processes' || active.startsWith('process-details'),
        linkProps: {
          to: Locations.processes(),
          state: {refreshContent: true, hideOptionalFilters: true},
        },
        onClick: () => {
          tracking.track({
            eventName: 'navigation',
            link: 'header-processes',
            currentPage,
          });
        },
      },
      {
        type: 'item',
        key: 'decisions',
        label: 'Decisions',
        icon: DecisionTree,
        isActive: (active: string) =>
          active === 'decisions' || active === 'decision-details',
        linkProps: {
          to: Locations.decisions(),
          state: {refreshContent: true, hideOptionalFilters: true},
        },
        onClick: () => {
          tracking.track({
            eventName: 'navigation',
            link: 'header-decisions',
            currentPage,
          });
        },
      },
      {
        type: 'group-item',
        key: 'operations',
        label: 'Operations',
        icon: Activity,
        isActive:
          currentPage === 'batch-operations' ||
          currentPage === 'operations-log',
        defaultExpanded:
          currentPage === 'batch-operations' ||
          currentPage === 'operations-log',
        linkProps: {to: Paths.batchOperations()},
        children: [
          {
            type: 'item',
            key: 'batch-operations',
            label: 'Batch operations',
            icon: DataTable,
            linkProps: {to: Paths.batchOperations()},
            onClick: () => {
              tracking.track({
                eventName: 'navigation',
                link: 'header-batch-operations',
                currentPage,
              });
              (document.activeElement as HTMLElement)?.blur();
            },
          },
          {
            type: 'item',
            key: 'operations-log',
            label: 'Operations log',
            icon: ListChecked,
            linkProps: {to: Paths.operationsLog()},
            onClick: () => {
              tracking.track({
                eventName: 'navigation',
                link: 'header-operations-log',
                currentPage,
              });
              (document.activeElement as HTMLElement)?.blur();
            },
          },
        ],
      },
    ];

    return children;
  }, [forbidden, currentPage]);
}

export {useSidebarChildren};
