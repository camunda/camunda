/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useCallback} from 'react';
import {useNavigate, useSearch} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {useSuspenseQuery} from '@tanstack/react-query';
import {getStateLocally} from '#/shared/browser-storage/local-storage';
import {CheckmarkIcon, OverflowMenu, OverflowMenuItem, SelectItem} from '#/shared/design-system-compat';
import {ArrowDownWideNarrow} from 'lucide-react';
import {queries} from '#/shared/http/queries';
import {featureFlags} from '#/shared/feature-flags';
import {cn} from '#/shared/cn';
import {GhostSelect} from './GhostSelect';
import {FilterSelectDS} from './FilterSelectDS';
import {CustomFiltersModal} from './custom-filters/CustomFiltersModal';
import {getCustomFilterSearch} from '../getCustomFilterSearch';
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
	const [isCustomFiltersModalOpen, setIsCustomFiltersModalOpen] = useState(false);

	const {data: username} = useSuspenseQuery({
		...queries.getCurrentUser(),
		select: ({username}) => username,
	});

	const search = useSearch({from: '/_auth/tasklist/_tasks'});

	const completionEligible = filter === 'completed' || filter === 'custom';
	const sortOptionsOrder = completionEligible ? COMPLETION_SORTING_OPTIONS_ORDER : SORTING_OPTIONS_ORDER;

	const onSort = (id: TasklistIndexSearch['sortBy']) => {
		navigate({to: '.', search: (prev) => ({...prev, sortBy: id})});
	};

	const closeModal = useCallback(() => {
		setIsCustomFiltersModalOpen(false);
	}, []);

	const handleFilterSuccess = useCallback(
		(filterId: string) => {
			closeModal();
			navigate({
				to: '.',
				search: getCustomFilterSearch({
					currentSearch: search,
					filter: filterId,
					username,
				}),
			});
		},
		[closeModal, navigate, search, username],
	);

	const BUILT_IN_FILTERS: {id: BuiltInFilter; labelKey: string}[] = [
		{id: 'all-open', labelKey: 'tasklist.taskFiltersAllOpenTasks'},
		{id: 'assigned-to-me', labelKey: 'tasklist.taskFiltersAssignedToMe'},
		{id: 'unassigned', labelKey: 'tasklist.taskFiltersUnassigned'},
		{id: 'completed', labelKey: 'tasklist.taskFiltersCompleted'},
	];

	const customFilters = Object.entries(getStateLocally('tasklist.customFilters') ?? {});

	const applyFilter = useCallback(
		(newFilter: BuiltInFilter | string) => {
			navigate({
				to: '.',
				search: newFilter === 'completed' ? {filter: newFilter, sortBy: 'completion'} : {filter: newFilter},
			});
		},
		[navigate],
	);

	if (featureFlags.dsTasklistUI) {
		return (
			<section className={cn(styles.panelHeader, styles.panelHeaderDS)} aria-label={t('tasklist.taskFiltersHeaderAria')}>
				<FilterSelectDS
					filter={filter}
					onFilterChange={applyFilter}
					onCreateFilter={() => {
						setIsCustomFiltersModalOpen(true);
					}}
				/>
				<CustomFiltersModal
					filterId={undefined}
					isOpen={isCustomFiltersModalOpen}
					onClose={closeModal}
					onSuccess={handleFilterSuccess}
					onDelete={closeModal}
				/>
				<OverflowMenu
					aria-label={t('tasklist.taskFiltersSortButton')}
					iconDescription={t('tasklist.taskFiltersSortButton')}
					renderIcon={ArrowDownWideNarrow}
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
									<CheckmarkIcon aria-label="" size={20} style={{visibility: sortBy === id ? undefined : 'hidden'}} />
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
	}

	return (
		<section className={styles.panelHeader} aria-label={t('tasklist.taskFiltersHeaderAria')}>
			<GhostSelect
				id="filter-select"
				hideLabel
				value={filter}
				onChange={(e) => {
					const newFilter = e.target.value as BuiltInFilter | string;
					if (newFilter === 'create-filter') {
						setIsCustomFiltersModalOpen(true);
					} else {
						navigate({to: '.', search: newFilter === 'completed' ? {filter: newFilter, sortBy: 'completion'} : {filter: newFilter}});
					}
				}}
			>
				{BUILT_IN_FILTERS.map(({id, labelKey}) => (
					<SelectItem key={id} value={id} text={t(labelKey)} />
				))}
				<SelectItem key="create-filter" value="create-filter" text={t('tasklist.taskFilterPanelNewFilter')} />
				{customFilters.map(([filterId, {name}]) => {
					const label = filterId === 'custom' || name === undefined ? t('tasklist.taskFilterPanelCustom') : name;
					return <SelectItem key={filterId} value={filterId} text={label} />;
				})}
			</GhostSelect>
			<CustomFiltersModal
				filterId={undefined}
				isOpen={isCustomFiltersModalOpen}
				onClose={closeModal}
				onSuccess={handleFilterSuccess}
				onDelete={closeModal}
			/>
			<OverflowMenu
				aria-label={t('tasklist.taskFiltersSortButton')}
				iconDescription={t('tasklist.taskFiltersSortButton')}
				renderIcon={ArrowDownWideNarrow}
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
								<CheckmarkIcon aria-label="" size={20} style={{visibility: sortBy === id ? undefined : 'hidden'}} />
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
