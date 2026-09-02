/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, TableHead} from '@camunda/design-system';
import {ArrowDown, ArrowUp, ArrowUpDown} from 'lucide-react';
import {useNavigate} from '@tanstack/react-router';
import {
	getNextSortSearchValue,
	getSortParams,
	type TaskDetailsHistorySearch,
	type TaskDetailsHistorySortField,
} from '../sortUtils';

const OrderIcon: React.FC<{sortOrder: 'asc' | 'desc'; isActive: boolean}> = ({sortOrder, isActive}) => {
	if (!isActive) {
		return <ArrowUpDown className="size-3.5 opacity-50" aria-hidden />;
	}

	return sortOrder === 'asc' ? (
		<ArrowUp className="size-3.5" aria-hidden />
	) : (
		<ArrowDown className="size-3.5" aria-hidden />
	);
};

type Props = {
	label: string;
	search: TaskDetailsHistorySearch;
	sortKey?: TaskDetailsHistorySortField;
	isDisabled: boolean;
	children: React.ReactNode;
};

const ColumnHeader: React.FC<Props> = ({sortKey, label, search, isDisabled, children}) => {
	const navigate = useNavigate();
	const sort = getSortParams(search);

	if (sortKey === undefined || isDisabled) {
		return <TableHead>{children || <span className="sr-only">{label}</span>}</TableHead>;
	}

	const isActive = sort.sortBy === sortKey;
	const currentSortOrder = isActive ? sort.sortOrder : undefined;

	return (
		<TableHead aria-sort={isActive ? (currentSortOrder === 'asc' ? 'ascending' : 'descending') : 'none'}>
			<Button
				variant="ghost"
				size="sm"
				className="-mx-2 h-auto gap-1 px-2 py-1 font-medium"
				onClick={() => {
					navigate({
						to: '.',
						search: (previous) => ({...previous, sort: getNextSortSearchValue(sortKey, currentSortOrder)}),
					});
				}}
				title={`Sort by ${label}`}
				aria-label={`Sort by ${label}`}
			>
				{children}
				<OrderIcon sortOrder={currentSortOrder ?? 'asc'} isActive={isActive} />
			</Button>
		</TableHead>
	);
};

export {ColumnHeader};
