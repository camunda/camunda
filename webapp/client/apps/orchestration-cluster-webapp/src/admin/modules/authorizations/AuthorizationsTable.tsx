/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {useTranslation} from 'react-i18next';
import {useQuery} from '@tanstack/react-query';
import {Trash2} from '@camunda/design-system/icons';
import {
	DataTable,
	Skeleton,
	type DataTableColumn,
	type DataTableRowAction,
	type SortingConfig,
} from '@camunda/design-system';
import type {Authorization, ResourceType} from '@camunda/camunda-api-zod-schemas/8.11';
import type {AdminClientConfig} from '#/shared/http/adminClientConfig';
import {queries} from '#/shared/http/queries';
import {getAuthorizationsRequestBody} from './getAuthorizationsRequestBody';
import {DEFAULT_PAGE_SIZE, getEffectiveSort, PAGE_SIZES, type AuthorizationsSearch} from './searchSchema';

type SortingState = NonNullable<SortingConfig['sortState']>;

type AuthorizationsTableProps = {
	search: AuthorizationsSearch;
	resourceType: ResourceType;
	clientConfig: AdminClientConfig;
	onSearchChange: (next: Partial<AuthorizationsSearch>) => void;
	onDelete: (authorization: Authorization) => void;
};

const AuthorizationsTable: React.FC<AuthorizationsTableProps> = ({
	search,
	resourceType,
	clientConfig,
	onSearchChange,
	onDelete,
}) => {
	const {t} = useTranslation();
	const isUserTask = resourceType === 'USER_TASK';

	const {data, isPlaceholderData} = useQuery({
		...queries.queryAuthorizations(getAuthorizationsRequestBody(search, resourceType)),
		throwOnError: true,
		// Sorting, paging and the owner filter keep the current rows on screen until the next ones
		// arrive. Another resource type changes the columns, so its rows must not show under them.
		placeholderData: (previousData, previousQuery) =>
			previousQuery?.queryKey[1].filter?.resourceType === resourceType ? previousData : undefined,
	});

	const columns = useMemo<DataTableColumn<Authorization>[]>(
		() => [
			{accessorKey: 'ownerType', header: t('admin.authorizations.ownerTypeColumn')},
			{accessorKey: 'ownerId', header: t('admin.authorizations.ownerIdColumn')},
			isUserTask
				? {
						accessorKey: 'resourcePropertyName',
						header: t('admin.authorizations.resourcePropertyNameColumn'),
						enableSorting: false,
					}
				: {accessorKey: 'resourceId', header: t('admin.authorizations.resourceIdColumn')},
			{
				id: 'permissionTypes',
				header: t('admin.authorizations.permissionsColumn'),
				accessorFn: ({permissionTypes}) => permissionTypes.join(', '),
				enableSorting: false,
			},
		],
		[isUserTask, t],
	);

	const rowActions = useMemo<DataTableRowAction<Authorization>[]>(
		() => [
			{
				id: 'delete',
				label: t('admin.authorizations.deleteAuthorization'),
				icon: <Trash2 aria-hidden />,
				iconOnly: true,
				variant: 'destructive',
				disabled: ({ownerType, ownerId}) => ownerType === 'ROLE' && clientConfig.defaultRoleIds.includes(ownerId),
				onClick: onDelete,
			},
		],
		[clientConfig.defaultRoleIds, onDelete, t],
	);

	const sortState = useMemo<SortingState>(() => {
		const {field, order} = getEffectiveSort(search, resourceType);
		return [{id: field, desc: order === 'desc'}];
	}, [resourceType, search]);

	const handleSortingChange = useCallback(
		(state: SortingState) => {
			const [sorted] = state;

			onSearchChange({
				sortField: sorted?.id as AuthorizationsSearch['sortField'],
				sortOrder: sorted === undefined ? undefined : sorted.desc ? 'desc' : 'asc',
				page: undefined,
			});
		},
		[onSearchChange],
	);

	const handlePaginationChange = useCallback(
		({pageIndex, pageSize}: {pageIndex: number; pageSize: number}) => {
			const nextPageSize = pageSize === DEFAULT_PAGE_SIZE ? undefined : (pageSize as AuthorizationsSearch['pageSize']);
			const hasPageSizeChanged = pageSize !== (search.pageSize ?? DEFAULT_PAGE_SIZE);

			onSearchChange({
				pageSize: nextPageSize,
				page: hasPageSizeChanged || pageIndex === 0 ? undefined : pageIndex + 1,
			});
		},
		[onSearchChange, search.pageSize],
	);

	if (data === undefined) {
		return (
			<div className="flex flex-col gap-2" data-testid="authorizations-skeleton">
				{Array.from({length: 5}, (_, index) => (
					<Skeleton className="h-10 w-full" key={index} />
				))}
			</div>
		);
	}

	return (
		<div aria-busy={isPlaceholderData} className={isPlaceholderData ? 'opacity-60 transition-opacity' : undefined}>
			<DataTable
				aria-label={t('admin.headerNavItemAuthorizations')}
				columns={columns}
				data={data.items}
				getRowId={(authorization) => authorization.authorizationKey}
				rowActions={rowActions}
				inlineActionsThreshold={2}
				sorting={{manual: true, sortState, onSortingChange: handleSortingChange}}
				pagination={{
					manual: true,
					pageSizes: [...PAGE_SIZES],
					defaultPageSize: DEFAULT_PAGE_SIZE,
					pageSize: search.pageSize ?? DEFAULT_PAGE_SIZE,
					pageIndex: (search.page ?? 1) - 1,
					rowCount: data.page.totalItems,
					onPaginationChange: handlePaginationChange,
				}}
				emptyState={t('admin.authorizations.noAuthorizationsFound')}
			/>
		</div>
	);
};

export {AuthorizationsTable};
