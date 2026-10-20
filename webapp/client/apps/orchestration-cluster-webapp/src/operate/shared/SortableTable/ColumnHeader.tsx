/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {TableHeader} from '@carbon/react';
import {useNavigate, useSearch} from '@tanstack/react-router';
import type {QuerySortOrder as SortOrder} from '@camunda/camunda-api-zod-schemas/8.11';
import {z} from 'zod';
import {createSortSearchParamSchema} from '#/shared/sortSearchParam';

const sortSearchParamSchema = createSortSearchParamSchema(z.string());

type Props = {
	sortKey: string;
	label: string;
	isDefault?: boolean;
	defaultOrder?: SortOrder;
	onSort?: (sortKey: string, order: SortOrder) => void;
};

const ColumnHeader: React.FC<Props> = ({sortKey, label, isDefault = false, defaultOrder = 'DESC', onSort}) => {
	const navigate = useNavigate();
	const search = useSearch({strict: false}) as {sort?: string};

	const {field: currentSortKey, order: currentSortOrder} = useMemo(
		() => sortSearchParamSchema.safeParse(search.sort).data ?? {field: '', order: undefined},
		[search.sort],
	);
	const isActive = currentSortKey === sortKey || (currentSortKey === '' && isDefault);
	const activeOrder: SortOrder =
		currentSortKey === sortKey && currentSortOrder !== undefined ? currentSortOrder : defaultOrder;

	const handleSort = () => {
		const newOrder: SortOrder =
			isActive && activeOrder === 'ASC' ? 'DESC' : isActive && activeOrder === 'DESC' ? 'ASC' : defaultOrder;
		onSort?.(sortKey, newOrder);
		void navigate({to: '.', search: (prev) => ({...prev, sort: `${sortKey}+${newOrder}`})});
	};

	return (
		<TableHeader
			isSortable
			isSortHeader={isActive}
			sortDirection={isActive ? activeOrder : 'NONE'}
			onClick={handleSort}
		>
			{label}
		</TableHeader>
	);
};

export {ColumnHeader};
