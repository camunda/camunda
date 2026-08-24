/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Outlet} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import type {CurrentUser, QueryUserTasksResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {AvailableTasks} from '#/tasklist/modules/available-tasks/shadcn.components/AvailableTasks';

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

const TasksLayoutPage: React.FC<Props> = ({
	pages,
	currentUser,
	hasNextPage,
	hasPreviousPage,
	onScrollDown,
	onScrollUp,
	isFetchingNextPage,
	isFetchingPreviousPage,
}) => {
	const {t} = useTranslation();

	return (
		<main id="main-content" className="grid h-full grid-cols-[19.5rem_minmax(0,1fr)] overflow-hidden">
			<section
				className="grid grid-rows-[3rem_minmax(0,1fr)] overflow-hidden"
				aria-label={t('tasklist.tasksPanelLabel')}
			>
				<header className="flex items-center border-b border-border px-4">
					<h1 className="sr-only">{t('tasklist.headerNavItemTasks')}</h1>
					<h2 className="text-sm font-medium">{t('tasklist.taskFiltersAllOpenTasks')}</h2>
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
			</section>
			<div className="overflow-auto border-l border-border">
				<Outlet />
			</div>
		</main>
	);
};

export {TasksLayoutPage};
