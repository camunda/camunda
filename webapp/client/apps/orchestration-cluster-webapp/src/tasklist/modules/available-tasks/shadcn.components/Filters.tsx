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
} from '@camunda/design-system';
import {useSuspenseQuery} from '@tanstack/react-query';
import {useNavigate, useSearch} from '@tanstack/react-router';
import {ArrowDownWideNarrow} from 'lucide-react';
import {useTranslation} from 'react-i18next';
import {tasklistIndexSearchSchema, type TasklistIndexSearch} from '#/tasklist/modules/available-tasks/searchSchema';
import {useCallback, useState} from 'react';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {queries} from '#/shared/http/queries';
import {getCustomFilterSearch} from '#/tasklist/modules/available-tasks/getCustomFilterSearch';
import {FilterSelect} from './FilterSelect';
import {CustomFiltersModal} from './custom-filters/CustomFiltersModal';
import {DeleteFilterModal} from './custom-filters/DeleteFilterModal';

type Props = {
	disabled?: boolean;
};

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
	const search = useSearch({from: '/shadcn/_auth/tasklist/_tasks'});
	const [isCustomFiltersModalOpen, setIsCustomFiltersModalOpen] = useState(false);
	const [customFilterToEdit, setCustomFilterToEdit] = useState<string | undefined>();
	const [customFilterToDelete, setCustomFilterToDelete] = useState<string | undefined>();
	const {data: username} = useSuspenseQuery({
		...queries.getCurrentUser(),
		select: ({username}) => username,
	});

	const completionEligible = filter === 'completed' || filter === 'custom';
	const sortOptionsOrder = completionEligible ? COMPLETION_SORTING_OPTIONS_ORDER : SORTING_OPTIONS_ORDER;

	const onFilterChange = useCallback(
		(newFilter: string) => {
			if (!['all-open', 'assigned-to-me', 'unassigned', 'completed'].includes(newFilter)) {
				navigate({
					to: '.',
					search: getCustomFilterSearch({currentSearch: search, filter: newFilter, username}),
				});
				return;
			}

			navigate({
				to: '.',
				search: newFilter === 'completed' ? {filter: newFilter, sortBy: 'completion'} : {filter: newFilter},
			});
		},
		[navigate, search, username],
	);

	const closeModal = useCallback(() => {
		setIsCustomFiltersModalOpen(false);
		setCustomFilterToEdit(undefined);
	}, []);

	const handleSuccess = useCallback(
		(filterId: string) => {
			closeModal();
			navigate({
				to: '.',
				search: getCustomFilterSearch({currentSearch: search, filter: filterId, username}),
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
			navigate({to: '.', search: {filter: 'all-open'}});
		}

		setCustomFilterToDelete(undefined);
	}, [deleteFilter, customFilterToDelete, filter, navigate]);

	const handleModalDelete = useCallback(
		(filterId: string) => {
			deleteFilter(filterId);
			navigate({to: '.', search: {filter: 'all-open'}});
			closeModal();
		},
		[deleteFilter, navigate, closeModal],
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
			<FilterSelect
				filter={filter}
				disabled={disabled}
				onFilterChange={onFilterChange}
				onCreateFilter={() => setIsCustomFiltersModalOpen(true)}
				onEditFilter={setCustomFilterToEdit}
				onDeleteFilter={setCustomFilterToDelete}
			/>
			<CustomFiltersModal
				filterId={customFilterToEdit}
				isOpen={isCustomFiltersModalOpen || customFilterToEdit !== undefined}
				onClose={closeModal}
				onSuccess={handleSuccess}
				onDelete={handleModalDelete}
			/>
			<DeleteFilterModal
				data-testid="direct-delete-filter-modal"
				filterId={customFilterToDelete ?? ''}
				isOpen={customFilterToDelete !== undefined}
				onClose={() => setCustomFilterToDelete(undefined)}
				onDelete={handleDelete}
			/>
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
