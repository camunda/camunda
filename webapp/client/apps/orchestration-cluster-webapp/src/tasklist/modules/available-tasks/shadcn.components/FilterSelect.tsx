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
	DropdownMenuItem,
	DropdownMenuLabel,
	DropdownMenuSeparator,
	DropdownMenuSub,
	DropdownMenuSubContent,
	DropdownMenuSubTrigger,
	DropdownMenuTrigger,
} from '@camunda/design-system';
import {Check, ChevronDown, EllipsisVertical, Plus} from 'lucide-react';
import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {getStateLocally} from '#/shared/browser-storage/local-storage';
import {isBuiltInFilter, type BuiltInFilter} from '#/tasklist/modules/available-tasks/searchSchema';

const BUILT_IN_FILTERS: {id: BuiltInFilter; labelKey: string}[] = [
	{id: 'all-open', labelKey: 'tasklist.taskFiltersAllOpenTasks'},
	{id: 'assigned-to-me', labelKey: 'tasklist.taskFiltersAssignedToMe'},
	{id: 'unassigned', labelKey: 'tasklist.taskFiltersUnassigned'},
	{id: 'completed', labelKey: 'tasklist.taskFiltersCompleted'},
];

type Props = {
	filter: BuiltInFilter | (string & {});
	disabled?: boolean;
	onFilterChange: (filter: BuiltInFilter | (string & {})) => void;
	onCreateFilter: () => void;
	onEditFilter: (filterId: string) => void;
	onDeleteFilter: (filterId: string) => void;
};

const FilterSelect: React.FC<Props> = ({
	filter,
	disabled = false,
	onFilterChange,
	onCreateFilter,
	onEditFilter,
	onDeleteFilter,
}) => {
	const {t} = useTranslation();
	const [isOpen, setIsOpen] = useState(false);
	const customFilters = Object.entries(getStateLocally('tasklist.customFilters') ?? {});

	function getCustomFilterLabel(filterId: string, name?: string): string {
		return filterId === 'custom' || name === undefined ? t('tasklist.taskFilterPanelCustom') : name;
	}

	function getTriggerLabel(): string {
		if (isBuiltInFilter(filter)) {
			const builtInFilter = BUILT_IN_FILTERS.find(({id}) => id === filter);
			return builtInFilter === undefined ? '' : t(builtInFilter.labelKey);
		}

		return getCustomFilterLabel(filter, getStateLocally('tasklist.customFilters')?.[filter]?.name);
	}

	return (
		<DropdownMenu open={isOpen} onOpenChange={setIsOpen}>
			<DropdownMenuTrigger asChild>
				<Button
					id="filter-select"
					variant="secondary"
					disabled={disabled}
					aria-label={t('tasklist.taskFiltersHeaderAria')}
					className="min-w-0 justify-between"
				>
					<span className="truncate">{getTriggerLabel()}</span>
					<ChevronDown aria-hidden />
				</Button>
			</DropdownMenuTrigger>

			<DropdownMenuContent align="start" className="grid min-w-52 grid-cols-[minmax(0,1fr)_2rem]">
				{BUILT_IN_FILTERS.map(({id, labelKey}) => (
					<DropdownMenuItem key={id} className="col-span-2 justify-between" onSelect={() => onFilterChange(id)}>
						<span>{t(labelKey)}</span>
						{filter === id ? <Check aria-hidden /> : null}
					</DropdownMenuItem>
				))}

				{customFilters.length > 0 ? (
					<>
						<DropdownMenuSeparator className="col-span-2" />
						<DropdownMenuLabel className="col-span-2">{t('tasklist.taskFiltersCustomFilter')}</DropdownMenuLabel>
						{customFilters.map(([filterId, {name}]) => {
							const label = getCustomFilterLabel(filterId, name);

							return (
								<DropdownMenuSub key={filterId}>
									<DropdownMenuItem className="min-w-0 justify-between" onSelect={() => onFilterChange(filterId)}>
										<span className="truncate">{label}</span>
										{filter === filterId ? <Check aria-hidden /> : null}
									</DropdownMenuItem>
									<DropdownMenuSubTrigger
										className="size-8 justify-center p-0 [&>svg:last-child]:hidden"
										aria-label={`${t('tasklist.taskFilterPanelCustomFilterActions')} - ${label}`}
										title={t('tasklist.taskFilterPanelCustomFilterActions')}
									>
										<EllipsisVertical aria-hidden />
									</DropdownMenuSubTrigger>
									<DropdownMenuSubContent>
										<DropdownMenuItem
											onSelect={() => {
												setIsOpen(false);
												onEditFilter(filterId);
											}}
										>
											{t('tasklist.taskFilterPanelEdit')}
										</DropdownMenuItem>
										<DropdownMenuSeparator />
										<DropdownMenuItem
											variant="destructive"
											onSelect={() => {
												setIsOpen(false);
												onDeleteFilter(filterId);
											}}
										>
											{t('tasklist.taskFilterPanelDelete')}
										</DropdownMenuItem>
									</DropdownMenuSubContent>
								</DropdownMenuSub>
							);
						})}
					</>
				) : null}

				<DropdownMenuSeparator className="col-span-2" />
				<DropdownMenuItem className="col-span-2" onSelect={onCreateFilter}>
					<Plus aria-hidden />
					{t('tasklist.taskFilterPanelNewFilter')}
				</DropdownMenuItem>
			</DropdownMenuContent>
		</DropdownMenu>
	);
};

export {FilterSelect};
