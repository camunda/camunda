/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Outlet, useMatchRoute} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import type {CurrentUser, QueryUserTasksResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {useMediaQuery} from '@camunda/design-system';
import {cn} from '#/shared/cn';
import {AvailableTasks} from '#/tasklist/modules/available-tasks/shadcn.components/AvailableTasks';
import {Filters} from '#/tasklist/modules/available-tasks/shadcn.components/Filters';
import {AutoSelectNextTaskToggle} from '#/tasklist/modules/available-tasks/shadcn.components/AutoSelectNextTaskToggle';

type Props = {
	pages: QueryUserTasksResponseBody[];
	currentUser: CurrentUser;
	isPending?: boolean;
	hasNextPage: boolean;
	hasPreviousPage: boolean;
	onScrollDown: () => Promise<void>;
	onScrollUp: () => Promise<void>;
	isFetchingNextPage?: boolean;
	isFetchingPreviousPage?: boolean;
};

const TasksLayoutPage: React.FC<Props> = ({
	pages,
	currentUser,
	isPending = false,
	hasNextPage,
	hasPreviousPage,
	onScrollDown,
	onScrollUp,
	isFetchingNextPage,
	isFetchingPreviousPage,
}) => {
	const {t} = useTranslation();
	const matchRoute = useMatchRoute();
	const isBelowMd = useMediaQuery('(width < 48rem)');
	const hasSelectedTask = matchRoute({to: '/shadcn/tasklist/$userTaskKey', fuzzy: true}) !== false;

	return (
		<main
			id="main-content"
			className={cn('grid h-full overflow-hidden', isBelowMd ? 'grid-cols-1' : 'grid-cols-[19.5rem_minmax(0,1fr)]')}
		>
			<section
				className={cn(
					'grid min-w-0 grid-rows-[3rem_minmax(0,1fr)_auto] overflow-hidden',
					isBelowMd && hasSelectedTask && 'hidden',
				)}
				aria-label={t('tasklist.tasksPanelLabel')}
			>
				<header className="flex items-center border-b border-border px-2">
					<h1 className="sr-only">{t('tasklist.headerNavItemTasks')}</h1>
					<Filters disabled={isPending} />
				</header>
				<AvailableTasks
					pages={pages}
					currentUser={currentUser}
					hasNextPage={hasNextPage}
					hasPreviousPage={hasPreviousPage}
					onScrollDown={onScrollDown}
					onScrollUp={onScrollUp}
					isFetchingNextPage={isFetchingNextPage}
					isFetchingPreviousPage={isFetchingPreviousPage}
				/>
				<AutoSelectNextTaskToggle />
			</section>
			<div
				className={cn(
					'min-w-0 overflow-auto',
					!isBelowMd && 'border-l border-border',
					isBelowMd && !hasSelectedTask && 'hidden',
				)}
			>
				<Outlet />
			</div>
		</main>
	);
};

export {TasksLayoutPage};
