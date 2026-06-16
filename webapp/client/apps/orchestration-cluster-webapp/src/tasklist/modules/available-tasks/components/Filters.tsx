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
import {tracking} from '#/shared/tracking';
import styles from './Filters.module.scss';
import type {TasklistIndexSearch} from '#/tasklist/modules/available-tasks/searchSchema';

const SORTING_OPTIONS_ORDER = [
	'creation',
	'due',
	'follow-up',
	'priority',
] as const satisfies TasklistIndexSearch['sortBy'][];

const COMPLETED_SORTING_OPTIONS_ORDER = [
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

const FILTER_HEADER_LABEL_KEYS: Record<TasklistIndexSearch['filter'], string> = {
	'all-open': 'tasklist.taskFiltersAllOpenTasks',
	'assigned-to-me': 'tasklist.taskFiltersAssignedToMe',
	unassigned: 'tasklist.taskFiltersUnassigned',
	completed: 'tasklist.taskFiltersCompleted',
};

const Filters: React.FC = () => {
	const {t} = useTranslation();
	const {sortBy, filter} = useSearch({from: '/_auth/tasklist/_tasks'});
	const navigate = useNavigate();

	const sortOptionsOrder = filter === 'completed' ? COMPLETED_SORTING_OPTIONS_ORDER : SORTING_OPTIONS_ORDER;

	return (
		<section className={styles.panelHeader} aria-label={t('tasklist.taskFiltersHeaderAria')}>
			<h1 className={styles.header}>{t(FILTER_HEADER_LABEL_KEYS[filter])}</h1>
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
							navigate({to: '.', search: (prev) => ({...prev, sortBy: id})});
							tracking.track({
								eventName: 'tasklist:tasks-filtered',
								filter,
								sorting: id,
								customFilters: [],
								customFilterVariableCount: 0,
							});
						}}
					/>
				))}
			</OverflowMenu>
		</section>
	);
};

export {Filters};
