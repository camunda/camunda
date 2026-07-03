/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TableHeader} from '@carbon/react';
import {useNavigate} from '@tanstack/react-router';
import {getSortParams, getSortSearchValue, type TaskDetailsHistorySearch} from '../sortUtils';

type Props = {
	label: string;
	search: TaskDetailsHistorySearch;
	sortKey?: string;
	isDisabled: boolean;
	children: React.ReactNode;
};

const ColumnHeader: React.FC<Props> = ({sortKey, label, search, isDisabled, children}) => {
	const navigate = useNavigate();
	const sort = getSortParams(search);

	if (sortKey === undefined || isDisabled) {
		return <TableHeader>{children}</TableHeader>;
	}

	const isActive = sort.sortBy === sortKey;
	const currentSortOrder = isActive ? sort.sortOrder : undefined;

	return (
		<TableHeader
			onClick={() => {
				void navigate({
					to: '.',
					search: (previous) => ({...previous, sort: getSortSearchValue(sortKey, currentSortOrder)}),
				});
			}}
			isSortHeader
			title={`Sort by ${label}`}
			aria-label={`Sort by ${label}`}
			sortDirection={isActive ? (currentSortOrder === 'asc' ? 'ASC' : 'DESC') : 'NONE'}
			isSortable
		>
			{children}
		</TableHeader>
	);
};

export {ColumnHeader};
