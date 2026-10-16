/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.11';
import {EmptyState, PageHeader, PageLayout} from '@camunda/design-system';
import {useSearch} from '@tanstack/react-router';
import {observer} from 'mobx-react-lite';
import {useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import type {TasklistIndexSearch} from '#/tasklist/modules/available-tasks/searchSchema';
import {ProcessTile} from '#/tasklist/modules/processes/components/ProcessTile';
import {ProcessesFilters} from '#/tasklist/modules/processes/components/ProcessesFilters';
import {NoTaskSelectedPage} from '#/tasklist/pages/NoTaskSelectedPage';
import {TasksLayoutPage} from '#/tasklist/pages/TasksLayoutPage';
import {themeEditorStore} from '#/tasklist/modules/theme-editor/themeEditorStore';
import {PREVIEW_PROCESSES, PREVIEW_TASKS, PREVIEW_USER} from './fixtures';

/*
 * Preview route components that react to the preview dataset (sample or empty). Actions that
 * would change data are no-ops.
 */

const noop = () => {};
const noopAsync = async () => {};

function matchesFilter(task: UserTask, filter: string) {
	switch (filter) {
		case 'assigned-to-me':
			return task.state === 'CREATED' && task.assignee === PREVIEW_USER.username;
		case 'unassigned':
			return task.state === 'CREATED' && task.assignee === null;
		case 'completed':
			return task.state === 'COMPLETED';
		default:
			return task.state === 'CREATED';
	}
}

function compareDates(a: string | null, b: string | null, order: 'asc' | 'desc') {
	if (a === b) {
		return 0;
	}

	if (a === null) {
		return 1;
	}

	if (b === null) {
		return -1;
	}

	return order === 'asc' ? a.localeCompare(b) : b.localeCompare(a);
}

function sortTasks(tasks: UserTask[], sortBy: TasklistIndexSearch['sortBy']) {
	return [...tasks].sort((a, b) => {
		switch (sortBy) {
			case 'priority':
				return (b.priority ?? 0) - (a.priority ?? 0);
			case 'due':
				return compareDates(a.dueDate, b.dueDate, 'asc');
			case 'follow-up':
				return compareDates(a.followUpDate, b.followUpDate, 'asc');
			case 'completion':
				return compareDates(a.completionDate, b.completionDate, 'desc');
			default:
				return compareDates(a.creationDate, b.creationDate, 'desc');
		}
	});
}

const PreviewTasksLayout: React.FC = observer(() => {
	const {filter, sortBy} = useSearch({from: '/_shadcn/_auth/tasklist/_tasks'});
	const isEmpty = themeEditorStore.dataset === 'empty';
	const pages = useMemo(() => {
		const items = isEmpty
			? []
			: sortTasks(
					PREVIEW_TASKS.filter((task) => matchesFilter(task, filter)),
					sortBy,
				);

		return [
			{
				items,
				page: {totalItems: items.length, startCursor: null, endCursor: null, hasMoreTotalItems: false},
			},
		];
	}, [filter, isEmpty, sortBy]);

	return (
		<TasksLayoutPage
			pages={pages}
			currentUser={PREVIEW_USER}
			hasNextPage={false}
			hasPreviousPage={false}
			onScrollDown={noopAsync}
			onScrollUp={noopAsync}
		/>
	);
});

const PreviewNoTaskSelected: React.FC = observer(() => (
	<NoTaskSelectedPage hasNoTasks={themeEditorStore.dataset === 'empty'} />
));
const PreviewProcesses: React.FC = observer(() => {
	const {t} = useTranslation();
	const search = useSearch({from: '/_shadcn/_auth/tasklist/processes'});
	const query = search.search?.toLowerCase() ?? '';
	const processes =
		themeEditorStore.dataset === 'empty'
			? []
			: PREVIEW_PROCESSES.filter(({name}) => (name ?? '').toLowerCase().includes(query)).filter(
					({hasStartForm}) => search.hasStartForm === undefined || hasStartForm === (search.hasStartForm === 'yes'),
				);

	return (
		<PageLayout>
			<div className="flex flex-col gap-6">
				<PageHeader title={t('tasklist.headerNavItemProcesses')} description={t('tasklist.processesSubtitle')} />
				<div className="flex flex-col gap-4">
					<ProcessesFilters initialFilterValues={search} tenants={PREVIEW_USER.tenants} />
					{processes.length === 0 ? (
						<EmptyState
							heading={
								query === ''
									? t('tasklist.processesProcessNotPublishedError')
									: t('tasklist.processesProcessNotFoundError')
							}
							description={t('tasklist.processesErrorBody')}
						/>
					) : (
						<div className="grid gap-4 max-[42rem]:grid-cols-1 min-[42rem]:grid-cols-2 min-[66rem]:grid-cols-3">
							{processes.map((process, index) => (
								<ProcessTile
									key={process.processDefinitionKey}
									process={process}
									status={index === 1 ? 'finished' : 'inactive'}
									isStartButtonDisabled={false}
									onStartProcess={noop}
								/>
							))}
						</div>
					)}
				</div>
			</div>
		</PageLayout>
	);
});
export {PreviewNoTaskSelected, PreviewProcesses, PreviewTasksLayout};
