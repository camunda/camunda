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

const SORTING_OPTION_LABEL_KEYS = {
	creation: 'taskFiltersSortCreationDate',
	due: 'taskFiltersSortDueDate',
	'follow-up': 'taskFiltersSortFollowUpDate',
	priority: 'taskFiltersSortPriority',
} as const;

const Filters: React.FC = () => {
	const {t} = useTranslation();
	const {sortBy} = useSearch({from: '/_auth/tasklist/'});
	const navigate = useNavigate();

	return (
		<section className={styles.panelHeader} aria-label={t('taskFiltersHeaderAria')}>
			<h1 className={styles.header}>{t('taskFiltersAllOpenTasks')}</h1>
			<OverflowMenu
				aria-label={t('taskFiltersSortButton')}
				iconDescription={t('taskFiltersSortButton')}
				renderIcon={SortAscending}
				size="md"
				align="bottom"
				menuOptionsClass={styles.overflowMenu}
			>
				{SORTING_OPTIONS_ORDER.map((id) => (
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
							navigate({to: '/tasklist', search: (prev) => ({...prev, sortBy: id})});
							tracking.track({
								eventName: 'tasklist:tasks-filtered',
								filter: 'all-open',
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
