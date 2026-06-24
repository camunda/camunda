/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {OverflowMenu, OverflowMenuItem} from '@carbon/react';
import {Checkmark, SortAscending} from '@carbon/react/icons';
import {useNavigate, useSearch} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {getStateLocally} from '#/shared/browser-storage/local-storage';
import {tracking} from '#/shared/tracking';
import styles from './Filters.module.scss';
import {
	isBuiltInFilter,
	type BuiltInFilter,
	type TasklistIndexSearch,
} from '#/tasklist/modules/available-tasks/searchSchema';

const SORTING_OPTIONS_ORDER = [
	'creation',
	'due',
	'follow-up',
	'priority',
] as const satisfies TasklistIndexSearch['sortBy'][];

const COMPLETION_SORTING_OPTIONS_ORDER = [
	'creation',
	'due',
	'follow-up',
	'priority',
	'completion',
] as const satisfies TasklistIndexSearch['sortBy'][];

const SORTING_OPTION_LABEL_KEYS = {
	creation: 'tasklist.taskFiltersSortCreationDate',
	due: 'tasklist.taskFiltersSortDueDate',
	'follow-up': 'tasklist.taskFiltersSortFollowUpDate',
	completion: 'tasklist.taskFiltersSortCompletionDate',
	priority: 'tasklist.taskFiltersSortPriority',
} as const;

const FILTER_HEADER_LABEL_KEYS: Record<BuiltInFilter, string> = {
	'all-open': 'tasklist.taskFiltersAllOpenTasks',
	'assigned-to-me': 'tasklist.taskFiltersAssignedToMe',
	unassigned: 'tasklist.taskFiltersUnassigned',
	completed: 'tasklist.taskFiltersCompleted',
};

function getFilterLabel(filter: string): string {
	if (isBuiltInFilter(filter)) {
		return FILTER_HEADER_LABEL_KEYS[filter];
	}

	const stored = getStateLocally('tasklist.customFilters')?.[filter];

	return stored?.name ?? 'tasklist.taskFilterPanelCustom';
}

const Filters: React.FC = () => {
	const {t} = useTranslation();
	const {sortBy, filter} = useSearch({from: '/_auth/tasklist/_tasks'});
	const navigate = useNavigate();

	const completionEligible = filter === 'completed' || filter === 'custom';
	const sortOptionsOrder = completionEligible ? COMPLETION_SORTING_OPTIONS_ORDER : SORTING_OPTIONS_ORDER;

	const onSort = (id: TasklistIndexSearch['sortBy']) => {
		navigate({to: '.', search: (prev) => ({...prev, sortBy: id})});
		const stored = !isBuiltInFilter(filter) ? getStateLocally('tasklist.customFilters')?.[filter] : undefined;
		tracking.track({
			eventName: 'tasklist:tasks-filtered',
			filter,
			sorting: id,
			customFilters: stored?.variables?.map((variable) => variable.name ?? '') ?? [],
			customFilterVariableCount: stored?.variables?.length ?? 0,
		});
	};

	return (
		<section className={styles.panelHeader} aria-label={t('tasklist.taskFiltersHeaderAria')}>
			<h1 className={styles.header}>{t(getFilterLabel(filter))}</h1>
			<OverflowMenu
				aria-label={t('tasklist.taskFiltersSortButton')}
				iconDescription={t('tasklist.taskFiltersSortButton')}
				renderIcon={SortAscending}
				size="md"
				align="bottom"
				menuOptionsClass={styles.overflowMenu}
			>
				{sortOptionsOrder.map((id) => (
					<OverflowMenuItem
						key={id}
						aria-selected={sortBy === id}
						itemText={
							<div className={styles.sortItem}>
								<Checkmark aria-label="" size={20} style={{visibility: sortBy === id ? undefined : 'hidden'}} />
								{t(SORTING_OPTION_LABEL_KEYS[id])}
							</div>
						}
						onClick={() => {
							onSort(id);
						}}
					/>
				))}
			</OverflowMenu>
		</section>
	);
};

export {Filters};
