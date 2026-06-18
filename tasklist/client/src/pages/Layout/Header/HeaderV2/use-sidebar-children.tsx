/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Flow, TaskView} from '@carbon/react/icons';
import type {SidebarNodeDescriptor} from '@camunda/camunda-composite-components';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {pages} from 'modules/routing';
import {tracking} from 'modules/tracking';
import {getStateLocally} from 'modules/local-storage';
import {isForbidden} from 'modules/utils/isForbidden';

/**
 * Top-level sidebar layout: a Tasks group containing the task-filter
 * children, plus a flat Processes entry. Empty when the user is not
 * authorised to view tasklist.
 */
export function useSidebarChildren(params: {
  currentUser: CurrentUser | undefined;
  isProcessesPage: boolean;
  taskFilterChildren: SidebarNodeDescriptor[];
}): SidebarNodeDescriptor[] {
  const {t} = useTranslation();
  const {currentUser, isProcessesPage, taskFilterChildren} = params;

  if (isForbidden(currentUser)) {
    return [];
  }

  return [
    {
      type: 'group-item',
      key: 'tasks',
      label: t('headerNavItemTasks'),
      icon: TaskView,
      defaultExpanded: !isProcessesPage,
      linkProps: {to: pages.initial},
      onClick: () => {
        tracking.track({eventName: 'navigation', link: 'header-tasks'});
      },
      children: taskFilterChildren,
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
}
