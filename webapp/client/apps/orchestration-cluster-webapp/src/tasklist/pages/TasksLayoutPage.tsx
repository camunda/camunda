/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Stack} from '#/shared/design-system-compat';
import type {CurrentUser, UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {AvailableTasks} from '#/tasklist/modules/available-tasks/components/AvailableTasks';
import {CollapsiblePanel} from '#/tasklist/modules/available-tasks/components/CollapsiblePanel';
import {Filters} from '#/tasklist/modules/available-tasks/components/Filters';
import {AutoSelectNextTaskToggle} from '#/tasklist/modules/available-tasks/components/AutoSelectNextTaskToggle';
import {featureFlags} from '#/shared/feature-flags';
import {cn} from '#/shared/cn';
import {useMediaQuery} from '@camunda/design-system';
import styles from './TasksLayoutPage.module.scss';
import {Outlet, useMatchRoute} from '@tanstack/react-router';

type Props = {
	tasks: UserTask[];
	currentUser: CurrentUser;
	hasNextPage: boolean;
	hasPreviousPage: boolean;
	onScrollDown: () => Promise<UserTask[]>;
	onScrollUp: () => Promise<UserTask[]>;
};

const TasksLayoutPage: React.FC<Props> = ({
	tasks,
	currentUser,
	hasNextPage,
	hasPreviousPage,
	onScrollDown,
	onScrollUp,
}) => {
	const {t} = useTranslation();
	const matchRoute = useMatchRoute();
	// DS-only: below `md` (--breakpoint-md, 48rem/768px) the task list and
	// task detail are separate single-pane views instead of side-by-side
	// columns — see TaskDetailsHeader.tsx's back button, the way back to
	// the list.
	const isBelowMd = useMediaQuery('(width < 48rem)');
	const isMobileSinglePane = featureFlags.dsTasklistUI && isBelowMd;
	const hasSelectedTask = matchRoute({to: '/tasklist/$userTaskKey', fuzzy: true}) !== false;

	return (
		<main
			id="main-content"
			className={cn(
				styles.container,
				featureFlags.dsTasklistUI && styles.containerDS,
				isMobileSinglePane && styles.containerMobileDS,
			)}
		>
			{/* DS-only: the rail moved up to the tasklist route so it is present on
			    Processes too (TasklistNavLayout.tsx), and its filters moved into the
			    filter picker below. Carbon keeps the filter panel here. */}
			{featureFlags.dsTasklistUI ? null : <CollapsiblePanel />}
			{isMobileSinglePane && hasSelectedTask ? null : (
				<Stack
					as="section"
					className={cn(
						styles.tasksPanel,
						featureFlags.dsTasklistUI && styles.tasksPanelDS,
						isMobileSinglePane && styles.tasksPanelMobileDS,
					)}
					aria-label={t('tasklist.tasksPanelLabel')}
				>
					<Filters />
					<AvailableTasks
						tasks={tasks}
						currentUser={currentUser}
						hasNextPage={hasNextPage}
						hasPreviousPage={hasPreviousPage}
						onScrollDown={onScrollDown}
						onScrollUp={onScrollUp}
					/>
					<AutoSelectNextTaskToggle />
				</Stack>
			)}
			{isMobileSinglePane && !hasSelectedTask ? null : (
				<div className={cn(styles.detailsPanel, featureFlags.dsTasklistUI && styles.detailsPanelDS)}>
					<Outlet />
				</div>
			)}
		</main>
	);
};

export {TasksLayoutPage};
