/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {matchPath, useLocation} from 'react-router-dom';
import {Bot, Branch, ChartLineSmooth, Dashboard, Folder, Task} from '@carbon/react/icons';
import type {CarbonIconType} from '@carbon/icons-react';
import type {NavIcon, SidebarNode} from '@camunda/design-system';

import {t} from 'translation';

/**
 * Carbon declares `tabIndex` as `string | number` in its icons' `propTypes`, which is
 * invariant against React's SVG props. The mismatch is type-only — nothing reads
 * `propTypes` — so the icon renders unchanged.
 */
function navIcon(icon: CarbonIconType): NavIcon {
  return icon as NavIcon;
}

function isCurrentPage(active: string[], pathname: string): boolean {
  return active.some((path) => matchPath(pathname, {path, exact: true}) !== null);
}

export default function useSidebarChildren(noActions?: boolean): SidebarNode[] {
  const {pathname} = useLocation();

  const isAnalysis = isCurrentPage(['/analysis/', '/analysis/*'], pathname);
  // Remembers a manual collapse only while the user stays on the same side of the
  // Analysis boundary; crossing it hands control back to the route.
  const [expansion, setExpansion] = useState({section: isAnalysis, expanded: isAnalysis});
  const analysisExpanded = expansion.section === isAnalysis ? expansion.expanded : isAnalysis;

  if (noActions) {
    return [];
  }

  const children: SidebarNode[] = [
    {
      type: 'item',
      key: 'dashboards',
      label: t('navigation.dashboards').toString(),
      icon: navIcon(Dashboard),
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
      icon: navIcon(Folder),
      linkProps: {to: '/collections'},
      isActive:
        isCurrentPage(['/collections/', '/report/*', '/dashboard/*', '/collection/*'], pathname) &&
        !isCurrentPage(['/dashboard/instant/*'], pathname),
    },
    {
      type: 'group-item',
      key: 'analysis',
      label: t('navigation.analysis').toString(),
      icon: navIcon(ChartLineSmooth),
      linkProps: {to: '/analysis'},
      isActive: isAnalysis,
      isExpanded: analysisExpanded,
      onToggleExpand: (expanded) => setExpansion({section: isAnalysis, expanded}),
      children: [
        {
          type: 'item',
          key: 'analysis-task',
          label: t('analysis.task.label').toString(),
          icon: navIcon(Task),
          linkProps: {to: '/analysis/taskAnalysis'},
          isActive: isCurrentPage(['/analysis/', '/analysis/taskAnalysis'], pathname),
        },
        {
          type: 'item',
          key: 'analysis-branch',
          label: t('analysis.branchAnalysis').toString(),
          icon: navIcon(Branch),
          linkProps: {to: '/analysis/branchAnalysis'},
          isActive: isCurrentPage(['/analysis/branchAnalysis'], pathname),
        },
      ],
    },
    {
      type: 'item',
      key: 'agentic-control-plane',
      label: t('navigation.agenticControlPlane').toString(),
      icon: navIcon(Bot),
      linkProps: {to: '/agentic-control-plane'},
      isActive: isCurrentPage(['/agentic-control-plane', '/agentic-control-plane/*'], pathname),
    },
  ];

  return children;
}
