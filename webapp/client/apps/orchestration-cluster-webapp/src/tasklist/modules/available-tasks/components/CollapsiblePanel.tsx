/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useState} from 'react';
// FLAG: ButtonSet has no carbon-compat adapter in @camunda/design-system
// (no button-set.* module in carbon-compat) — no DS equivalent exists yet, so
// it stays on @carbon/react until the DS team ships one. See
// docs/migration/human-follow-up.md ("FLAG symbols"). All other symbols in
// this file are migrated to the feature-flagged #/shared/design-system-compat
// swap point below.
import {ButtonSet} from '@carbon/react';
import {
	Button,
	FilterIcon,
	Layer,
	OverflowMenu,
	OverflowMenuItem,
	SidePanelCloseIcon,
	SidePanelOpenIcon,
} from '#/shared/design-system-compat';
import {AppSidebar, TooltipProvider, type SidebarNode} from '@camunda/design-system';
import {CircleCheck, CircleDashed, CircleUser, Filter, ListTodo, Plus} from 'lucide-react';
import {Link, useNavigate, useSearch} from '@tanstack/react-router';
import {useSuspenseQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {usePrevious} from '@uidotdev/usehooks';
import {cn} from '#/shared/cn';
import {queries} from '#/shared/http/queries';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {featureFlags} from '#/shared/feature-flags';
import {FILTER_VALUES} from '#/tasklist/modules/available-tasks/searchSchema';
import {getCustomFilterSearch} from '#/tasklist/modules/available-tasks/getCustomFilterSearch';
import {CustomFiltersModal} from './custom-filters/CustomFiltersModal';
import {DeleteFilterModal} from './custom-filters/DeleteFilterModal';
import styles from './CollapsiblePanel.module.scss';

type BuiltInFilter = (typeof FILTER_VALUES)[number];

const BUILT_IN_FILTERS: {id: BuiltInFilter; labelKey: string}[] = [
	{id: 'all-open', labelKey: 'tasklist.taskFilterPanelAllOpenTasks'},
	{id: 'assigned-to-me', labelKey: 'tasklist.taskFilterPanelAssignedToMe'},
	{id: 'unassigned', labelKey: 'tasklist.taskFilterPanelUnassigned'},
	{id: 'completed', labelKey: 'tasklist.taskFilterPanelCompleted'},
];

// AppSidebar's SidebarItem requires an icon (there's no icon-less variant) — this
// UI previously had none, so these are new. Chosen to match existing semantic
// icon choices already used elsewhere in this migration (docs/kb/carbon-icons-to-lucide.md
// precedent + this file's own compat exports): CircleDashed for unassigned
// (matches CircleDashIcon/AssigneeTag), CircleCheck for completed (matches
// CheckmarkFilledIcon), CircleUser for assignee (matches UserAvatarIcon). No
// existing precedent for "all open tasks" or a generic custom-filter icon —
// ListTodo and Filter (matches this file's own FilterIcon) are new choices.
const BUILT_IN_FILTER_ICONS: Record<BuiltInFilter, typeof ListTodo> = {
	'all-open': ListTodo,
	'assigned-to-me': CircleUser,
	unassigned: CircleDashed,
	completed: CircleCheck,
};

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

	const deleteFilterModal = (
		<DeleteFilterModal
			data-testid="direct-delete-filter-modal"
			filterId={customFilterToDelete ?? ''}
			isOpen={customFilterToDelete !== undefined}
			onClose={() => {
				setCustomFilterToDelete(undefined);
			}}
			onDelete={handleDelete}
		/>
	);

	if (featureFlags.dsTasklistUI) {
		const items: SidebarNode[] = [
			...BUILT_IN_FILTERS.map(
				({id, labelKey}): SidebarNode => ({
					type: 'item',
					key: id,
					label: t(labelKey),
					icon: BUILT_IN_FILTER_ICONS[id],
					isActive: id === filter,
					onClick: () => {
						navigate({to: '.', search: id === 'completed' ? {filter: id, sortBy: 'completion'} : {filter: id}});
					},
				}),
			),
			{
				type: 'item',
				key: 'new-filter',
				label: t('tasklist.taskFilterPanelNewFilter'),
				icon: Plus,
				onClick: openModal,
			},
			...customFilters.map(([filterId, {name}]): SidebarNode => {
				const label = filterId === 'custom' || name === undefined ? t('tasklist.taskFilterPanelCustom') : name;
				return {
					type: 'item',
					key: filterId,
					label,
					icon: Filter,
					isActive: filter === filterId,
					onClick: () => {
						navigate({
							to: '.',
							search: getCustomFilterSearch({currentSearch: search, filter: filterId, username}),
						});
					},
					trailingElement: (
						<OverflowMenu
							iconDescription={t('tasklist.taskFilterPanelCustomFilterActions')}
							size="md"
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
					),
				};
			}),
		];

		return (
			<>
				{/* AppSidebar's collapsed rail wraps every item (and its own collapse
				    toggle) in a <Tooltip>, which throws without an ancestor
				    TooltipProvider — this repo has no app-root one yet (same gap noted
				    in design-system-compat/IconButton.tsx). Self-contained per-instance
				    provider as a stopgap, same precedent as IconButton.tsx. */}
				<TooltipProvider>
					<AppSidebar
						ariaLabel={t('tasklist.taskFilterPanelControlsAria')}
						items={items}
						expanded={!isCollapsed}
						onExpandedChange={(expanded) => setIsCollapsed(!expanded)}
						resizable={false}
						expandedWidth="12.25rem"
						// 3.5rem, not 2.5rem: AppSidebar's inner content div has its own
						// px-2 (16px) padding, and its collapsed nav buttons are
						// min-h-10 (40px). At 2.5rem (40px) rail width, the buttons only
						// get 24px of width after that padding — squashed, not square.
						// 3.5rem gives them the full 40px back, matching height.
						collapsedWidth="3.5rem"
						className={styles.dsSidebar}
					/>
				</TooltipProvider>
				{filtersModal}
				{deleteFilterModal}
			</>
		);
	}

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
							renderIcon={SidePanelOpenIcon}
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
							renderIcon={FilterIcon}
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
						renderIcon={SidePanelCloseIcon}
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
					{/* FLAG: ButtonSet has no carbon-compat adapter — left on @carbon/react
					    (see import above + docs/migration/human-follow-up.md). Its child
					    Button is already migrated to the DS swap point. */}
					<ButtonSet>
						<Button kind="ghost" size="md" onClick={openModal}>
							{t('tasklist.taskFilterPanelNewFilter')}
						</Button>
					</ButtonSet>
				</div>
			</nav>
			{filtersModal}
			{deleteFilterModal}
		</Layer>
	);
};

export {CollapsiblePanel};
