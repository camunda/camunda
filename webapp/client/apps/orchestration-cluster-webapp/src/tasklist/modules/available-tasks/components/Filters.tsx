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
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {CheckmarkIcon, OverflowMenu, OverflowMenuItem, SelectItem} from '#/shared/design-system-compat';
import {Button, DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger} from '@camunda/design-system';
import {ArrowDownWideNarrow} from 'lucide-react';
import {queries} from '#/shared/http/queries';
import {featureFlags} from '#/shared/feature-flags';
import {cn} from '#/shared/cn';
import {GhostSelect} from './GhostSelect';
import {FilterSelectDS} from './FilterSelectDS';
import {CustomFiltersModal} from './custom-filters/CustomFiltersModal';
import {DeleteFilterModal} from './custom-filters/DeleteFilterModal';
import {getCustomFilterSearch} from '../getCustomFilterSearch';
import styles from './Filters.module.scss';
import {type BuiltInFilter, type TasklistIndexSearch} from '#/tasklist/modules/available-tasks/searchSchema';

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

const Filters: React.FC = () => {
	const {t} = useTranslation();
	const {sortBy, filter} = useSearch({from: '/_auth/tasklist/_tasks'});
	const navigate = useNavigate();
	const [isCustomFiltersModalOpen, setIsCustomFiltersModalOpen] = useState(false);
	const [customFilterToEdit, setCustomFilterToEdit] = useState<string | undefined>();
	const [customFilterToDelete, setCustomFilterToDelete] = useState<string | undefined>();

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

	// DS-only workaround for the same Radix body-lock leak handled in
	// handleDeleteConfirmed below (see its comment for the full writeup, and
	// CollapsiblePanel.tsx for the original report). Closing this modal can
	// coincide with a re-render of whatever launched/reflects it (e.g.
	// FilterSelectDS updating once a new or edited filter is applied), which
	// breaks Radix's layer-count bookkeeping the same way — leaving the whole
	// page unable to receive clicks with no dialog left on screen.
	const releaseLeakedPointerLock = useCallback(() => {
		if (!featureFlags.dsTasklistUI) {
			return;
		}

		requestAnimationFrame(() => {
			const openLayer = document.querySelector(
				'[role="dialog"], [role="alertdialog"], [role="menu"], [data-radix-popper-content-wrapper]',
			);

			if (openLayer === null && document.body.style.pointerEvents === 'none') {
				document.body.style.pointerEvents = '';
			}
		});
	}, []);

	const closeModal = useCallback(() => {
		setIsCustomFiltersModalOpen(false);
		releaseLeakedPointerLock();
	}, [releaseLeakedPointerLock]);

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

	const closeEditModal = useCallback(() => {
		setCustomFilterToEdit(undefined);
		releaseLeakedPointerLock();
	}, [releaseLeakedPointerLock]);

	const handleDeleteConfirmed = useCallback(() => {
		if (customFilterToDelete === undefined) {
			return;
		}

		const storedFilters = getStateLocally('tasklist.customFilters') ?? {};
		const {[customFilterToDelete]: _removed, ...remainingFilters} = storedFilters;
		storeStateLocally('tasklist.customFilters', remainingFilters);

		if (filter === customFilterToDelete) {
			navigate({to: '.', search: {filter: 'all-open'}});
		}

		setCustomFilterToDelete(undefined);
		releaseLeakedPointerLock();
	}, [customFilterToDelete, filter, navigate, releaseLeakedPointerLock]);

	if (featureFlags.dsTasklistUI) {
		return (
			<section
				className={cn(styles.panelHeader, styles.panelHeaderDS)}
				aria-label={t('tasklist.taskFiltersHeaderAria')}
			>
				<FilterSelectDS
					filter={filter}
					onFilterChange={applyFilter}
					onCreateFilter={() => {
						setIsCustomFiltersModalOpen(true);
					}}
					onEditFilter={setCustomFilterToEdit}
					onDeleteFilter={setCustomFilterToDelete}
				/>
				<CustomFiltersModal
					filterId={undefined}
					isOpen={isCustomFiltersModalOpen}
					onClose={closeModal}
					onSuccess={handleFilterSuccess}
					onDelete={closeModal}
				/>
				<CustomFiltersModal
					filterId={customFilterToEdit}
					isOpen={customFilterToEdit !== undefined}
					onClose={closeEditModal}
					onSuccess={(filterId) => {
						closeEditModal();
						handleFilterSuccess(filterId);
					}}
					onDelete={closeEditModal}
				/>
				<DeleteFilterModal
					filterId={customFilterToDelete ?? ''}
					isOpen={customFilterToDelete !== undefined}
					onClose={() => {
						setCustomFilterToDelete(undefined);
					}}
					onDelete={handleDeleteConfirmed}
				/>
				{/* DS-only: carbon-compat's OverflowMenu drops the `align` prop entirely
				    (confirmed via its own warnDroppedProps), so the underlying Radix
				    DropdownMenuContent always falls back to align="center" — the menu
				    ends up centered under the trigger instead of flush with its right
				    edge. No prop/className path through the compat shim can fix that,
				    so this uses the DS's own DropdownMenu directly instead (REMAP, not
				    SWAP) — align="end" per explicit request. Carbon keeps OverflowMenu
				    unchanged below. */}
				<DropdownMenu>
					<DropdownMenuTrigger asChild>
						<Button
							variant="ghost"
							size="icon"
							aria-label={t('tasklist.taskFiltersSortButton')}
							title={t('tasklist.taskFiltersSortButton')}
						>
							<ArrowDownWideNarrow aria-hidden />
						</Button>
					</DropdownMenuTrigger>
					<DropdownMenuContent align="end">
						{sortOptionsOrder.map((id) => (
							<DropdownMenuItem
								key={id}
								aria-selected={sortBy === id}
								onClick={() => {
									onSort(id);
								}}
							>
								{/* Checkmark on the right, per explicit request — label first,
								    indicator last, with the row spread full-width so it lands
								    at the item's trailing edge instead of right after the label. */}
								<div className={cn(styles.sortItem, styles.sortItemDS)}>
									{t(SORTING_OPTION_LABEL_KEYS[id])}
									<CheckmarkIcon aria-label="" size={20} style={{visibility: sortBy === id ? undefined : 'hidden'}} />
								</div>
							</DropdownMenuItem>
						))}
					</DropdownMenuContent>
				</DropdownMenu>
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
						navigate({
							to: '.',
							search: newFilter === 'completed' ? {filter: newFilter, sortBy: 'completion'} : {filter: newFilter},
						});
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
