/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Link} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {AppSidebar, TooltipProvider, type SidebarNode} from '@camunda/design-system';
import {ListTodo, Workflow} from 'lucide-react';
import {useHasRouteMatch} from '#/shared/useHasRouteMatch';
import styles from './TasklistNavLayout.module.scss';

// Labels, routes and active-state rules are the ones the header navigation
// already uses (shared/header/useNavbar.ts), so the two surfaces cannot drift
// apart.
const NAV_ITEMS = [
	{key: 'tasks', labelKey: 'tasklist.headerNavItemTasks', to: '/tasklist', icon: ListTodo},
	{key: 'processes', labelKey: 'tasklist.headerNavItemProcesses', to: '/tasklist/processes', icon: Workflow},
] as const;

// Shared with the <SidebarProvider> that wraps this layout (see
// routes/_auth/route.tsx) — its `collapsedWidth`/`defaultWidth` must match
// these, since once a provider is present AppSidebar sources its expanded
// width from the provider's own state, not from the `expandedWidth` prop
// below (see AppSidebar's `resizedWidth` — `ctx ? ctx.width : ...`).
export const SIDEBAR_COLLAPSED_WIDTH = '3.5rem';
export const SIDEBAR_EXPANDED_WIDTH = '12.25rem';

type Props = {
	children: React.ReactNode;
};

/**
 * DS-only. Wraps every Tasklist page in the navigation rail, so switching
 * between Tasks and Processes works from either page. The rail used to live
 * inside the tasks layout, where it carried the task filters and was therefore
 * absent on Processes; the filters now live in the filter picker above the task
 * list (see FilterSelectDS.tsx).
 */
const TasklistNavLayout: React.FC<Props> = ({children}) => {
	const {t} = useTranslation();
	const hasRouteMatch = useHasRouteMatch();

	const items: SidebarNode[] = NAV_ITEMS.map(({key, labelKey, to, icon}): SidebarNode => ({
		type: 'item',
		key,
		label: t(labelKey),
		icon,
		// Same rule as the header navigation: the tasks entry covers the task
		// list and every task detail route, but yields to processes.
		isActive:
			key === 'processes'
				? hasRouteMatch('/tasklist/processes')
				: !hasRouteMatch('/tasklist/processes') &&
					hasRouteMatch(
						'/tasklist',
						'/tasklist/$userTaskKey',
						'/tasklist/$userTaskKey/process',
						'/tasklist/$userTaskKey/history',
					),
		// Rendered through TanStack's Link so these stay real anchors —
		// middle-click and open-in-new-tab keep working. Both keys are needed:
		// the installed AppSidebar decides a row is a link on `href` alone
		// (`node.linkProps?.href !== undefined`) while Link routes on `to`. The
		// published type documents `to` as sufficient, so this can drop to `{to}`
		// once the package catches up — logged in docs/migration/human-follow-up.md.
		linkProps: {href: to, to},
	}));

	return (
		// AppSidebar's collapsed rail wraps every item (and its own collapse toggle)
		// in a <Tooltip>, which throws without an ancestor TooltipProvider — this
		// repo has no app-root one yet (same gap noted in
		// design-system-compat/IconButton.tsx). Self-contained per-instance provider
		// as a stopgap, same precedent as IconButton.tsx.
		<TooltipProvider>
			{/* Expand/collapse and mobile-overlay state come from the
			    <SidebarProvider> wrapping this layout (routes/_auth/route.tsx) —
			    no `expanded`/`onExpandedChange` here, so AppSidebar falls back to
			    the provider's own state instead of a locally-controlled one. Below
			    `md` the provider's `isMobile` flips AppSidebar into a Sheet overlay
			    opened by AppHeader's hamburger, in place of the fixed rail. */}
			<AppSidebar
				ariaLabel={t('tasklist.taskPanelNavAria')}
				items={items}
				linkComponent={Link}
				resizable={false}
				expandedWidth={SIDEBAR_EXPANDED_WIDTH}
				// 3.5rem, not 2.5rem: AppSidebar's inner content div has its own px-2
				// (16px) padding, and its collapsed nav buttons are min-h-10 (40px). At
				// 2.5rem (40px) rail width, the buttons only get 24px of width after
				// that padding — squashed, not square. 3.5rem gives them the full 40px
				// back, matching height.
				collapsedWidth={SIDEBAR_COLLAPSED_WIDTH}
				className={styles.sidebar}
			/>
			{/* The rail is fixed, so it is out of flow: the pages need an equal inset
			    to sit beside it rather than under it. Reads `--app-sidebar-width`,
			    published by the wrapping <SidebarProvider> — 0px on mobile, where
			    the rail is a Sheet overlay instead of an in-flow column. */}
			<div className={styles.content}>{children}</div>
		</TooltipProvider>
	);
};

export {TasklistNavLayout};
