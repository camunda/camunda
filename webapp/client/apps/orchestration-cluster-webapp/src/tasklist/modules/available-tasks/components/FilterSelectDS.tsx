/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// DS-only filter picker (see Filters.tsx for the flag dispatch).
//
// Built on DropdownMenu rather than Select, deliberately, for two reasons:
//   1. The flag-off compat `Select` is a Carbon native <select>, which cannot
//      render separators, group labels or icons inside options at all.
//   2. A Radix Select cannot host a nested menu. Verified against the running
//      app: an actions trigger inside an open SelectContent closed the Select on
//      pointerdown, never opened its menu, and leaked `pointer-events: none`
//      onto <body>. stopPropagation does not help — the Select's dismissable
//      layer closes as focus moves into the nested layer.
//
// NOT IMPLEMENTED: per-custom-filter hover actions (edit/delete) inside this
// menu. Attempted with `DropdownMenuSub` wrapped alongside each
// `DropdownMenuRadioItem`; the wrapper div breaks Radix's menu item collection,
// so moving to the sub-trigger dismissed the whole menu and leaked the body
// lock again. Radix menus require items to be collection-managed descendants,
// which rules out a row that is "one option plus one trailing control". Editing
// and deleting a custom filter remain available from the sidebar's own overflow
// menu, which is what CollapsiblePanel already renders. Logged for the DS team
// in docs/migration/human-follow-up.md.

import {
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuLabel,
	DropdownMenuRadioGroup,
	DropdownMenuRadioItem,
	DropdownMenuSeparator,
	DropdownMenuTrigger,
} from '@camunda/design-system';
import {Check, ChevronDown, Plus} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import {getStateLocally} from '#/shared/browser-storage/local-storage';
import {isBuiltInFilter, type BuiltInFilter} from '#/tasklist/modules/available-tasks/searchSchema';
import styles from './FilterSelectDS.module.scss';

const BUILT_IN_FILTERS: {id: BuiltInFilter; labelKey: string}[] = [
	{id: 'all-open', labelKey: 'tasklist.taskFiltersAllOpenTasks'},
	{id: 'assigned-to-me', labelKey: 'tasklist.taskFiltersAssignedToMe'},
	{id: 'unassigned', labelKey: 'tasklist.taskFiltersUnassigned'},
	{id: 'completed', labelKey: 'tasklist.taskFiltersCompleted'},
];

type Props = {
	filter: string;
	onFilterChange: (filter: BuiltInFilter | string) => void;
	onCreateFilter: () => void;
};

const FilterSelectDS: React.FC<Props> = ({filter, onFilterChange, onCreateFilter}) => {
	const {t} = useTranslation();
	const customFilters = Object.entries(getStateLocally('tasklist.customFilters') ?? {});

	function getCustomFilterLabel(filterId: string, name?: string): string {
		return filterId === 'custom' || name === undefined ? t('tasklist.taskFilterPanelCustom') : name;
	}

	function getTriggerLabel(): string {
		if (isBuiltInFilter(filter)) {
			const builtIn = BUILT_IN_FILTERS.find(({id}) => id === filter);
			return builtIn === undefined ? '' : t(builtIn.labelKey);
		}

		return getCustomFilterLabel(filter, getStateLocally('tasklist.customFilters')?.[filter]?.name);
	}

	return (
		<DropdownMenu>
			<DropdownMenuTrigger
				id="filter-select"
				aria-label={t('tasklist.taskFiltersHeaderAria')}
				className={styles.trigger}
			>
				<span className={styles.triggerLabel}>{getTriggerLabel()}</span>
				<ChevronDown className={styles.triggerIcon} aria-hidden />
			</DropdownMenuTrigger>

			<DropdownMenuContent align="start" className={styles.menu}>
				<DropdownMenuRadioGroup
					value={filter}
					onValueChange={(next) => {
						onFilterChange(next);
					}}
				>
					{BUILT_IN_FILTERS.map(({id, labelKey}) => (
						<DropdownMenuRadioItem key={id} value={id} className={styles.option}>
							{t(labelKey)}
							{filter === id ? <Check className={styles.optionCheck} aria-hidden /> : null}
						</DropdownMenuRadioItem>
					))}
				</DropdownMenuRadioGroup>

				{customFilters.length > 0 ? (
					<>
						<DropdownMenuSeparator />
						<DropdownMenuLabel className={styles.groupLabel}>
							{t('tasklist.taskFilterPanelCustomFiltersGroup')}
						</DropdownMenuLabel>
						<DropdownMenuRadioGroup
							value={filter}
							onValueChange={(next) => {
								onFilterChange(next);
							}}
						>
							{customFilters.map(([filterId, {name}]) => (
								<DropdownMenuRadioItem key={filterId} value={filterId} className={styles.option}>
									{getCustomFilterLabel(filterId, name)}
									{filter === filterId ? <Check className={styles.optionCheck} aria-hidden /> : null}
								</DropdownMenuRadioItem>
							))}
						</DropdownMenuRadioGroup>
					</>
				) : null}

				{/* Kept last so it stays the final option however many custom filters
				    exist, rather than being buried between the two groups. */}
				<DropdownMenuSeparator />

				<DropdownMenuItem onSelect={onCreateFilter}>
					<Plus aria-hidden />
					{t('tasklist.taskFilterPanelNewFilter')}
				</DropdownMenuItem>
			</DropdownMenuContent>
		</DropdownMenu>
	);
};

export {FilterSelectDS};
