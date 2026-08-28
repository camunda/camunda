/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
	Button,
	DropdownMenu,
	DropdownMenuContent,
	DropdownMenuRadioGroup,
	DropdownMenuRadioItem,
	DropdownMenuTrigger,
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from '@camunda/design-system';
import {useNavigate, useSearch} from '@tanstack/react-router';
import {ArrowDownWideNarrow} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import {
	isBuiltInFilter,
	tasklistIndexSearchSchema,
	type BuiltInFilter,
	type TasklistIndexSearch,
} from '#/tasklist/modules/available-tasks/searchSchema';
import {useCallback} from 'react';

type Props = {
	disabled?: boolean;
};

const BUILT_IN_FILTERS: {id: BuiltInFilter; labelKey: string}[] = [
	{id: 'all-open', labelKey: 'tasklist.taskFiltersAllOpenTasks'},
	{id: 'assigned-to-me', labelKey: 'tasklist.taskFiltersAssignedToMe'},
	{id: 'unassigned', labelKey: 'tasklist.taskFiltersUnassigned'},
	{id: 'completed', labelKey: 'tasklist.taskFiltersCompleted'},
];

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

const Filters: React.FC<Props> = ({disabled = false}) => {
	const {t} = useTranslation();
	const {sortBy, filter} = useSearch({from: '/shadcn/_auth/tasklist/_tasks'});
	const navigate = useNavigate();

	const completionEligible = filter === 'completed' || filter === 'custom';
	const sortOptionsOrder = completionEligible ? COMPLETION_SORTING_OPTIONS_ORDER : SORTING_OPTIONS_ORDER;

	const onFilterChange = useCallback(
		(newFilter: string) => {
			navigate({
				to: '.',
				search: {
					filter: newFilter,
					sortBy: newFilter === 'completed' ? 'completion' : 'creation',
				},
			});
		},
		[navigate],
	);

	const onSort = useCallback(
		(id: string) => {
			const result = tasklistIndexSearchSchema.shape.sortBy.safeParse(id);
			if (result.success) {
				navigate({to: '.', search: (prev) => ({...prev, sortBy: result.data})});
			}
		},
		[navigate],
	);

	return (
		<section
			className="flex w-full items-center justify-between gap-2"
			aria-label={t('tasklist.taskFiltersHeaderAria')}
		>
			{/* custom filters aren't selectable here yet (camunda/camunda#60225); the placeholder covers that case */}
			<Select value={isBuiltInFilter(filter) ? filter : ''} onValueChange={onFilterChange} disabled={disabled}>
				<SelectTrigger aria-label={t('tasklist.taskFiltersHeaderAria')}>
					<SelectValue placeholder={t('tasklist.taskFilterPanelCustom')} />
				</SelectTrigger>
				<SelectContent>
					{BUILT_IN_FILTERS.map(({id, labelKey}) => (
						<SelectItem key={id} value={id}>
							{t(labelKey)}
						</SelectItem>
					))}
				</SelectContent>
			</Select>
			<DropdownMenu>
				<DropdownMenuTrigger asChild>
					<Button
						variant="ghost"
						size="icon"
						disabled={disabled}
						aria-label={t('tasklist.taskFiltersSortButton')}
						title={t('tasklist.taskFiltersSortButton')}
					>
						<ArrowDownWideNarrow aria-hidden />
					</Button>
				</DropdownMenuTrigger>
				<DropdownMenuContent align="end">
					<DropdownMenuRadioGroup value={sortBy} onValueChange={onSort}>
						{sortOptionsOrder.map((id) => (
							<DropdownMenuRadioItem key={id} value={id}>
								{t(SORTING_OPTION_LABEL_KEYS[id])}
							</DropdownMenuRadioItem>
						))}
					</DropdownMenuRadioGroup>
				</DropdownMenuContent>
			</DropdownMenu>
		</section>
	);
};

export {Filters};
