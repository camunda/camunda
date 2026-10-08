/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useRef} from 'react';
import type {CurrentUser, QueryUserTasksResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {useVirtualizer} from '@tanstack/react-virtual';
import {useTranslation} from 'react-i18next';
import {NoTasks} from './NoTasks';
import {Task} from './Task';

const ESTIMATED_TASK_HEIGHT = 312;
const OVERSCAN = 5;
const TASK_GAP = 8;
const LIST_PADDING = 8;

type Props = {
	pages: QueryUserTasksResponseBody[];
	currentUser: CurrentUser;
	hasNextPage: boolean;
	hasPreviousPage: boolean;
	onScrollDown: () => Promise<void>;
	onScrollUp: () => Promise<void>;
	isFetchingNextPage?: boolean;
	isFetchingPreviousPage?: boolean;
};

const AvailableTasks: React.FC<Props> = ({
	pages,
	currentUser,
	hasNextPage,
	hasPreviousPage,
	onScrollDown,
	onScrollUp,
	isFetchingNextPage = false,
	isFetchingPreviousPage = false,
}) => {
	const scrollContainerRef = useRef<HTMLDivElement | null>(null);
	const {t} = useTranslation();
	const tasks = useMemo(() => pages.flatMap((page) => page.items), [pages]);
	const totalItems = pages[0]?.page.totalItems ?? tasks.length;

	// eslint-disable-next-line react-hooks/incompatible-library
	const virtualizer = useVirtualizer<HTMLDivElement, HTMLDivElement>({
		count: totalItems,
		getScrollElement: () => scrollContainerRef.current,
		estimateSize: () => ESTIMATED_TASK_HEIGHT,
		getItemKey: (index) => tasks[index]?.userTaskKey ?? index,
		overscan: OVERSCAN,
		gap: TASK_GAP,
		paddingStart: LIST_PADDING,
		paddingEnd: LIST_PADDING,
	});
	const virtualItems = virtualizer.getVirtualItems();
	const firstVirtualIndex = virtualItems[0]?.index;
	const lastVirtualIndex = virtualItems.at(-1)?.index;

	useEffect(() => {
		if (lastVirtualIndex !== undefined && lastVirtualIndex >= tasks.length - 1 && hasNextPage && !isFetchingNextPage) {
			void onScrollDown();
		}
	}, [hasNextPage, isFetchingNextPage, lastVirtualIndex, onScrollDown, tasks.length]);

	useEffect(() => {
		if (firstVirtualIndex === 0 && hasPreviousPage && !isFetchingPreviousPage) {
			void onScrollUp();
		}
	}, [firstVirtualIndex, hasPreviousPage, isFetchingPreviousPage, onScrollUp]);

	return (
		<div className="h-full w-full overflow-hidden" title={t('tasklist.availableTasksTitle')}>
			{totalItems === 0 ? (
				<NoTasks />
			) : (
				<div
					ref={scrollContainerRef}
					className="h-full w-full overflow-y-auto"
					data-testid="scrollable-list"
					tabIndex={-1}
				>
					<div className="relative w-full" style={{height: virtualizer.getTotalSize()}}>
						{virtualItems.map((virtualTask) => {
							const task = tasks[virtualTask.index];
							if (task === undefined) {
								return null;
							}

							return (
								<div
									key={virtualTask.key}
									data-index={virtualTask.index}
									ref={virtualizer.measureElement}
									className="absolute top-0 left-0 w-full px-2"
									style={{transform: `translateY(${virtualTask.start}px)`}}
								>
									<Task
										userTaskKey={task.userTaskKey}
										displayName={task.name ?? task.elementId}
										processDisplayName={task.processName ?? task.processDefinitionId}
										businessId={task.businessId}
										assignee={task.assignee}
										creationDate={task.creationDate}
										followUpDate={task.followUpDate}
										dueDate={task.dueDate}
										completionDate={task.completionDate}
										priority={task.priority}
										currentUser={currentUser}
									/>
								</div>
							);
						})}
					</div>
				</div>
			)}
		</div>
	);
};

export {AvailableTasks};
