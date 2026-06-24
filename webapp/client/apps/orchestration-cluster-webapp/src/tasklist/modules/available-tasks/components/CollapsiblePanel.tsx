/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useState} from 'react';
import {Button, ButtonSet, Layer, OverflowMenu, OverflowMenuItem} from '@carbon/react';
import {Filter, SidePanelClose, SidePanelOpen} from '@carbon/react/icons';
import {Link, useNavigate, useSearch} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {usePrevious} from '@uidotdev/usehooks';
import {cn} from '#/shared/cn';
import {queries} from '#/shared/http/queries';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {FILTER_VALUES} from '#/tasklist/modules/available-tasks/searchSchema';
import {getCustomFilterSearch} from '#/tasklist/modules/available-tasks/getCustomFilterSearch';
import {CustomFiltersModal} from './custom-filters/CustomFiltersModal';
import {DeleteFilterModal} from './custom-filters/DeleteFilterModal';
import styles from './CollapsiblePanel.module.scss';
import {tracking} from '#/shared/tracking';

type BuiltInFilter = (typeof FILTER_VALUES)[number];

const BUILT_IN_FILTERS: {id: BuiltInFilter; labelKey: string}[] = [
	{id: 'all-open', labelKey: 'tasklist.taskFilterPanelAllOpenTasks'},
	{id: 'assigned-to-me', labelKey: 'tasklist.taskFilterPanelAssignedToMe'},
	{id: 'unassigned', labelKey: 'tasklist.taskFilterPanelUnassigned'},
	{id: 'completed', labelKey: 'tasklist.taskFilterPanelCompleted'},
];

const ELLIPSIS_CUTOFF_LENGTH = 17;

const CollapsiblePanel: React.FC = () => {
	const [isCollapsed, setIsCollapsed] = useState(true);
	const [isCustomFiltersModalOpen, setIsCustomFiltersModalOpen] = useState(false);
	const [customFilterToEdit, setCustomFilterToEdit] = useState<string | undefined>();
	const [customFilterToDelete, setCustomFilterToDelete] = useState<string | undefined>();
	const wasCollapsed = usePrevious(isCollapsed);
	const {t} = useTranslation();
	const search = useSearch({from: '/_auth/tasklist/_tasks'});
	const {filter} = search;
	const navigate = useNavigate();
	const {data: username} = useSuspenseQuery({
		...queries.getCurrentUser(),
		select: ({username}) => username,
	});
	const customFilters = Object.entries(getStateLocally('tasklist.customFilters') ?? {});

	const openModal = useCallback(() => {
		setIsCustomFiltersModalOpen(true);
	}, []);

	const closeModal = useCallback(() => {
		setIsCustomFiltersModalOpen(false);
		setCustomFilterToEdit(undefined);
	}, []);

	const handleSuccess = useCallback(
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

	const deleteFilter = useCallback((filterId: string) => {
		const storedFilters = getStateLocally('tasklist.customFilters') ?? {};
		const {[filterId]: _, ...remainingFilters} = storedFilters;

		storeStateLocally('tasklist.customFilters', remainingFilters);
		tracking.track({
			eventName: 'tasklist:custom-filter-deleted',
		});
	}, []);

	const handleDelete = useCallback(() => {
		deleteFilter(customFilterToDelete!);

		if (filter === customFilterToDelete) {
			navigate({
				to: '.',
				search: {filter: 'all-open'},
			});
		}

		setCustomFilterToDelete(undefined);
	}, [deleteFilter, customFilterToDelete, filter, navigate]);

	const handleModalDelete = useCallback(
		(filterId: string) => {
			deleteFilter(filterId);

			navigate({
				to: '.',
				search: {filter: 'all-open'},
			});

			closeModal();
		},
		[deleteFilter, navigate, closeModal],
	);

	const filtersModal = (
		<CustomFiltersModal
			key="custom-filters-modal"
			filterId={customFilterToEdit}
			isOpen={isCustomFiltersModalOpen || customFilterToEdit !== undefined}
			onClose={closeModal}
			onSuccess={handleSuccess}
			onDelete={handleModalDelete}
		/>
	);

	if (isCollapsed) {
		return (
			<Layer
				as="nav"
				id="task-nav-bar"
				className={cn(styles.base, styles.collapsedContainer)}
				aria-label={t('tasklist.taskFilterPanelControlsAria')}
			>
				<ul aria-labelledby="task-nav-bar">
					<li>
						<Button
							hasIconOnly
							renderIcon={SidePanelOpen}
							iconDescription={t('tasklist.taskFilterPanelExpandButton')}
							tooltipPosition="right"
							kind="ghost"
							size="md"
							onClick={() => {
								setIsCollapsed(false);
							}}
							aria-controls="task-nav-bar"
							aria-expanded="false"
							autoFocus={wasCollapsed !== null && !wasCollapsed}
						/>
					</li>
					<li>
						<Button
							hasIconOnly
							renderIcon={Filter}
							iconDescription={t('tasklist.taskFilterPanelFilterButton')}
							tooltipPosition="right"
							kind="ghost"
							size="md"
							onClick={openModal}
						/>
					</li>
				</ul>
				{filtersModal}
			</Layer>
		);
	}

	return (
		<Layer className={styles.floatingContainer}>
			<nav className={cn(styles.base, styles.expandedContainer)} id="task-nav-bar" aria-labelledby="filters-title">
				<div className={styles.panelHeader}>
					<h2 id="filters-title">{t('tasklist.taskFilterPanelTitle')}</h2>
					<Button
						hasIconOnly
						renderIcon={SidePanelClose}
						iconDescription={t('tasklist.taskFilterPanelCollapse')}
						tooltipPosition="right"
						kind="ghost"
						size="md"
						onClick={() => {
							setIsCollapsed(true);
						}}
						aria-controls="task-nav-bar"
						aria-expanded="true"
						autoFocus
					/>
				</div>
				<div className={styles.scrollContainer}>
					<ul aria-labelledby="task-nav-bar">
						{BUILT_IN_FILTERS.map(({id, labelKey}) => (
							<li key={id}>
								<Link
									to="."
									search={id === 'completed' ? {filter: id, sortBy: 'completion'} : {filter: id}}
									className={cn(styles.filterItem, {[styles.active!]: id === filter})}
									aria-current={id === filter ? 'page' : undefined}
									activeOptions={{
										includeSearch: true,
										exact: true,
									}}
								>
									{t(labelKey)}
								</Link>
							</li>
						))}
						{customFilters.map(([filterId, {name}]) => {
							const label = filterId === 'custom' || name === undefined ? t('tasklist.taskFilterPanelCustom') : name;

							return (
								<li className={styles.customFilterContainer} key={filterId}>
									<Link
										to="."
										search={getCustomFilterSearch({
											currentSearch: search,
											filter: filterId,
											username,
										})}
										className={cn(styles.filterItem, styles.customFilterNav, {
											[styles.active!]: filter === filterId,
										})}
										aria-current={filter === filterId ? 'page' : undefined}
										title={label.length > ELLIPSIS_CUTOFF_LENGTH ? label : undefined}
										activeOptions={{
											includeSearch: true,
											exact: true,
										}}
									>
										{label}
									</Link>
									<OverflowMenu
										iconDescription={t('tasklist.taskFilterPanelCustomFilterActions')}
										size="md"
										className={cn(styles.overflowMenu, {
											[styles.selected!]: filter === filterId,
										})}
										direction="top"
										flipped
										align="top-end"
									>
										<OverflowMenuItem
											itemText={t('tasklist.taskFilterPanelEdit')}
											onClick={() => {
												setCustomFilterToEdit(filterId);
											}}
										/>
										<OverflowMenuItem
											hasDivider
											isDelete
											itemText={t('tasklist.taskFilterPanelDelete')}
											onClick={() => {
												setCustomFilterToDelete(filterId);
											}}
										/>
									</OverflowMenu>
								</li>
							);
						})}
					</ul>
					<ButtonSet>
						<Button kind="ghost" size="md" onClick={openModal}>
							{t('tasklist.taskFilterPanelNewFilter')}
						</Button>
					</ButtonSet>
				</div>
			</nav>
			{filtersModal}
			<DeleteFilterModal
				data-testid="direct-delete-filter-modal"
				filterId={customFilterToDelete ?? ''}
				isOpen={customFilterToDelete !== undefined}
				onClose={() => {
					setCustomFilterToDelete(undefined);
				}}
				onDelete={handleDelete}
			/>
		</Layer>
	);
};

export {CollapsiblePanel};
