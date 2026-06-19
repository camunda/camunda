/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';
import {
  Add,
  Edit,
  Flow,
  TaskAdd,
  TaskComplete,
  TaskRemove,
  TaskView,
  TrashCan,
  UserFollow,
} from '@carbon/react/icons';
import type {SidebarNodeDescriptor} from '@camunda/camunda-composite-components';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {pages} from 'modules/routing';
import {tracking} from 'modules/tracking';
import {getStateLocally} from 'modules/local-storage';
import {isForbidden} from 'modules/utils/isForbidden';
import {useMemo} from 'react';
import {matchPath, useLocation, useSearchParams} from 'react-router-dom';
import {useCustomFiltersContext} from 'modules/tasks/available-tasks/CollapsiblePanel/CustomFiltersModal/CustomFiltersContext';
import {IconButton} from '@carbon/react';
import styles from '../styles.module.scss';
import {getNavLinkSearchParam} from 'modules/features/tasks/filters/getNavLinkSearchParam';

const stopRowNavigation = (e: React.SyntheticEvent) => {
  e.stopPropagation();
  e.preventDefault();
};

function useSidebarChildren(params: {
  currentUser: CurrentUser | undefined;
}): SidebarNodeDescriptor[] {
  const {currentUser} = params;
  const location = useLocation();
  const {customFilters, startEditing, startDeleting, startAdding, status} =
    useCustomFiltersContext();
  const [currentParams] = useSearchParams();

  const tasksChildren = useMemo(() => {
    return [
      {
        type: 'item' as const,
        key: 'tasks:all-open',
        label: t('taskFiltersAllOpenTasks'),
        icon: TaskView,
        linkProps: {
          to: {
            search: getNavLinkSearchParam({
              currentParams,
              username: currentUser?.username ?? '',
              filter: 'all-open',
            }),
          },
        },
        onClick: () => {
          tracking.track({
            eventName: 'navigation',
            link: 'header-tasks-all-open',
          });
        },
      },
      {
        type: 'item' as const,
        key: 'tasks:assigned-to-me',
        label: t('taskFiltersAssignedToMe'),
        icon: UserFollow,
        linkProps: {
          to: {
            search: getNavLinkSearchParam({
              currentParams,
              username: currentUser?.username ?? '',
              filter: 'assigned-to-me',
            }),
          },
        },
        onClick: () => {
          tracking.track({
            eventName: 'navigation',
            link: 'header-tasks-assigned-to-me',
          });
        },
      },
      {
        type: 'item' as const,
        key: 'tasks:unassigned',
        label: t('taskFiltersUnassigned'),
        icon: TaskRemove,
        linkProps: {
          to: {
            search: getNavLinkSearchParam({
              currentParams,
              username: currentUser?.username ?? '',
              filter: 'unassigned',
            }),
          },
        },
        onClick: () => {
          tracking.track({
            eventName: 'navigation',
            link: 'header-tasks-unassigned',
          });
        },
      },
      {
        type: 'item' as const,
        key: 'tasks:completed',
        label: t('taskFiltersCompleted'),
        icon: TaskComplete,
        linkProps: {
          to: {
            search: getNavLinkSearchParam({
              currentParams,
              username: currentUser?.username ?? '',
              filter: 'completed',
            }),
          },
        },
        onClick: () => {
          tracking.track({
            eventName: 'navigation',
            link: 'header-tasks-completed',
          });
        },
      },
      ...Object.entries(customFilters).map(([key, {name}]) => ({
        type: 'item' as const,
        key: `tasks:${key}`,
        label: name ?? key,
        icon: TaskAdd,
        linkProps: {
          to: {
            search: getNavLinkSearchParam({
              currentParams,
              username: currentUser?.username ?? '',
              filter: key,
            }),
          },
        },
        onClick: () => {
          tracking.track({
            eventName: 'navigation',
            link: `header-tasks-custom-${key}`,
          });
        },
        trailingElement: (
          <span className={styles.hoverActions} onClick={stopRowNavigation}>
            <IconButton
              kind="ghost"
              size="sm"
              label={t('taskFilterPanelEdit')}
              align="bottom-left"
              autoAlign
              disabled={status !== 'initial'}
              onClick={() => {
                startEditing(key);
              }}
            >
              <Edit />
            </IconButton>
            <IconButton
              kind="ghost"
              size="sm"
              label={t('taskFilterPanelDelete')}
              align="bottom-left"
              disabled={status !== 'initial'}
              autoAlign
              onClick={() => {
                startDeleting(key);
              }}
            >
              <TrashCan />
            </IconButton>
          </span>
        ),
      })),
      {
        type: 'item' as const,
        key: 'tasks:new-filter',
        label: t('taskFilterPanelNewFilter'),
        icon: Add,
        onClick: startAdding,
      },
    ];
  }, [
    customFilters,
    startAdding,
    startDeleting,
    startEditing,
    status,
    currentUser,
    currentParams,
  ]);

  // @ts-expect-error - we need to fix it from the C3 side
  return useMemo(() => {
    if (isForbidden(currentUser)) {
      return [];
    }

    const isProcessesPage =
      matchPath(pages.processes(), location.pathname) !== null;

    return [
      {
        type: 'group-item',
        key: 'tasks',
        label: t('headerNavItemTasks'),
        icon: TaskView,
        defaultExpanded: !isProcessesPage,
        children: tasksChildren,
        linkProps: {to: pages.initial},
        onClick: () => {
          tracking.track({eventName: 'navigation', link: 'header-tasks'});
        },
      },
      {
        type: 'item',
        key: 'processes',
        label: t('headerNavItemProcesses'),
        icon: Flow,
        isActive: () => isProcessesPage,
        linkProps: {
          to: pages.processes({
            tenantId: getStateLocally('tenantId') ?? undefined,
          }),
        },
        onClick: () => {
          tracking.track({eventName: 'navigation', link: 'header-processes'});
        },
      },
    ];
  }, [currentUser, location, tasksChildren]);
}

export {useSidebarChildren};
