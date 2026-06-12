/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Stack} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import type {CurrentUser, UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {AvailableTasks} from '#/tasklist/modules/available-tasks/components/AvailableTasks';
import {CollapsiblePanel} from '#/tasklist/modules/available-tasks/components/CollapsiblePanel';
import {Filters} from '#/tasklist/modules/available-tasks/components/Filters';
import {Options} from '#/tasklist/modules/available-tasks/components/Options';
import styles from './TasksLayoutPage.module.scss';
import {Outlet} from '@tanstack/react-router';

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

	return (
		<main className={styles.container}>
			<CollapsiblePanel />
			<Stack as="section" className={styles.tasksPanel} aria-label={t('tasksPanelLabel')}>
				<Filters />
				<AvailableTasks
					tasks={tasks}
					currentUser={currentUser}
					hasNextPage={hasNextPage}
					hasPreviousPage={hasPreviousPage}
					onScrollDown={onScrollDown}
					onScrollUp={onScrollUp}
				/>
				<Options />
			</Stack>
			<div className={styles.detailsPanel}>
				<Outlet />
			</div>
		</main>
	);
};

export {TasksLayoutPage};
