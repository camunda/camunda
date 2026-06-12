/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef} from 'react';
import {useTranslation} from 'react-i18next';
import type {CurrentUser, UserTask} from '@camunda/camunda-api-zod-schemas/8.10';
import {cn} from '#/shared/cn';
import {NoTasks} from './NoTasks';
import {Task} from './Task';
import styles from './AvailableTasks.module.scss';

type Props = {
	tasks: UserTask[];
	currentUser: CurrentUser;
	hasNextPage: boolean;
	hasPreviousPage: boolean;
	onScrollDown: () => Promise<UserTask[]>;
	onScrollUp: () => Promise<UserTask[]>;
};

const AvailableTasks: React.FC<Props> = ({
	tasks,
	currentUser,
	hasNextPage,
	hasPreviousPage,
	onScrollDown,
	onScrollUp,
}) => {
	const taskRef = useRef<HTMLDivElement | null>(null);
	const scrollableListRef = useRef<HTMLDivElement | null>(null);
	const {t} = useTranslation();

	return (
		<div
			className={cn(styles.container, {
				[styles.containerPadding!]: tasks.length === 0,
			})}
			title={t('availableTasksTitle')}
		>
			{tasks.length > 0 ? (
				<div
					className={styles.listContainer}
					data-testid="scrollable-list"
					ref={scrollableListRef}
					onScroll={async (event) => {
						const target = event.target as HTMLDivElement;

						if (Math.floor(target.scrollHeight - target.clientHeight - target.scrollTop) <= 0 && hasNextPage) {
							await onScrollDown();
						} else if (target.scrollTop === 0 && hasPreviousPage) {
							const previousTasks = await onScrollUp();

							target.scrollTop = (taskRef.current?.clientHeight ?? 0) * previousTasks.length;
						}
					}}
					tabIndex={-1}
				>
					{tasks.map((task) => (
						<Task
							ref={taskRef}
							key={task.userTaskKey}
							taskId={task.userTaskKey.toString()}
							displayName={task.name ?? task.elementId}
							processDisplayName={task.processName ?? task.processDefinitionId}
							assignee={task.assignee}
							creationDate={task.creationDate}
							followUpDate={task.followUpDate}
							dueDate={task.dueDate}
							completionDate={task.completionDate}
							priority={task.priority}
							currentUser={currentUser}
						/>
					))}
				</div>
			) : (
				<NoTasks />
			)}
		</div>
	);
};

export {AvailableTasks};
