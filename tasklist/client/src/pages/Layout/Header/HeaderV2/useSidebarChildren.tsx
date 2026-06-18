/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';
import {Flow, TaskView} from '@carbon/react/icons';
import type {SidebarNodeDescriptor} from '@camunda/camunda-composite-components';
import type {CurrentUser} from '@camunda/camunda-api-zod-schemas/8.10';
import {pages} from 'modules/routing';
import {tracking} from 'modules/tracking';
import {getStateLocally} from 'modules/local-storage';
import {isForbidden} from 'modules/utils/isForbidden';
import {useMemo} from 'react';
import {matchPath, useLocation} from 'react-router-dom';

function useSidebarChildren(params: {
  currentUser: CurrentUser | undefined;
}): SidebarNodeDescriptor[] {
  const {currentUser} = params;
  const location = useLocation();

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
        children: [],
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
  }, [currentUser, location]);
}

export {useSidebarChildren};
