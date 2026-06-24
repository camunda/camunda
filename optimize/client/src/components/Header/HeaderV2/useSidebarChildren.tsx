/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {matchPath, useLocation} from 'react-router-dom';
import {Bot, Branch, ChartLineSmooth, Dashboard, Folder, Task} from '@carbon/react/icons';
import type {SidebarNodeDescriptor} from '@camunda/camunda-composite-components';

import {t} from 'translation';

function isCurrentPage(active: string[], pathname: string): boolean {
  return active.some((path) => matchPath(pathname, {path, exact: true}) !== null);
}

export default function useSidebarChildren(noActions?: boolean): SidebarNodeDescriptor[] {
  const {pathname} = useLocation();

  if (noActions) {
    return [];
  }

  const isAnalysis = isCurrentPage(['/analysis/', '/analysis/*'], pathname);

  const children = [
    {
      type: 'item',
      key: 'dashboards',
      label: t('navigation.dashboards').toString(),
      icon: Dashboard,
      linkProps: {to: '/'},
      isActive: isCurrentPage(
        ['/', '/processes/', '/processes/*', '/dashboard/instant/*'],
        pathname
      ),
    },
    {
      type: 'item',
      key: 'collections',
      label: t('navigation.collections').toString(),
      icon: Folder,
      linkProps: {to: '/collections'},
      isActive:
        isCurrentPage(['/collections/', '/report/*', '/dashboard/*', '/collection/*'], pathname) &&
        !isCurrentPage(['/dashboard/instant/*'], pathname),
    },
    {
      type: 'group-item',
      key: 'analysis',
      label: t('navigation.analysis').toString(),
      icon: ChartLineSmooth,
      linkProps: {to: '/analysis'},
      isActive: isAnalysis,
      defaultExpanded: isAnalysis,
      children: [
        {
          type: 'item',
          key: 'analysis-task',
          label: t('analysis.task.label').toString(),
          icon: Task,
          linkProps: {to: '/analysis/taskAnalysis'},
          isActive: isCurrentPage(['/analysis/', '/analysis/taskAnalysis'], pathname),
        },
        {
          type: 'item',
          key: 'analysis-branch',
          label: t('analysis.branchAnalysis').toString(),
          icon: Branch,
          linkProps: {to: '/analysis/branchAnalysis'},
          isActive: isCurrentPage(['/analysis/branchAnalysis'], pathname),
        },
      ],
    },
    {
      type: 'item',
      key: 'agentic-control-plane',
      label: t('navigation.agenticControlPlane').toString(),
      icon: Bot,
      linkProps: {to: '/agentic-control-plane'},
      isActive: isCurrentPage(['/agentic-control-plane', '/agentic-control-plane/*'], pathname),
    },
  ];

  // @ts-expect-error - we need to fix it from the C3 side
  return children;
}
