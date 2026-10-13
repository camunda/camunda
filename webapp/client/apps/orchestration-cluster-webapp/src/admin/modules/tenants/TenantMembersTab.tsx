/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Plus, Trash2} from '@camunda/design-system/icons';
import {
	Button,
	DataTable,
	type DataTableColumn,
	type DataTableRowAction,
	type SortingConfig,
} from '@camunda/design-system';
import {AssignMembersModal} from './AssignMembersModal';
import {UnassignMemberModal} from './UnassignMemberModal';
import {DEFAULT_PAGE_SIZE, PAGE_SIZES} from './searchSchema';
import type {TenantDetailSearch, TenantTab} from './detailSearchSchema';

type SortingState = NonNullable<SortingConfig['sortState']>;

type Props<T> = {
	tab: TenantTab;
	tenantId: string;
	rows: T[];
	columns: DataTableColumn<T>[];
	sortColumnId: string;
	getMemberId: (row: T) => string;
	totalItems: number;
	search: TenantDetailSearch;
	isFreeText?: boolean;
	onSearchChange: (next: Partial<TenantDetailSearch>) => void;
};

function TenantMembersTab<T>({
	tab,
	tenantId,
	rows,
	columns,
	sortColumnId,
	getMemberId,
	totalItems,
	search,
	isFreeText = false,
	onSearchChange,
}: Props<T>) {
	const {t} = useTranslation();
	const [isAssigning, setIsAssigning] = useState(false);
	const [unassigningId, setUnassigningId] = useState<string | null>(null);

	const sortState = useMemo<SortingState>(
		() => [{id: sortColumnId, desc: search.sortOrder === 'DESC'}],
		[sortColumnId, search.sortOrder],
	);

	const rowActions = useMemo<DataTableRowAction<T>[]>(
		() => [
			{
				id: 'unassign',
				label: t('admin.tenants.unassignButton'),
				icon: <Trash2 aria-hidden />,
				variant: 'destructive',
				onClick: (row) => setUnassigningId(getMemberId(row)),
			},
		],
		[getMemberId, t],
	);

	return (
		<div className="flex flex-col gap-4">
			<div className="flex justify-end">
				<Button onClick={() => setIsAssigning(true)}>
					<Plus aria-hidden />
					{t(`admin.tenants.assignButton_${tab}`)}
				</Button>
			</div>
			<DataTable
				aria-label={t(`admin.tenants.tab_${tab}`)}
				className="**:data-[slot=data-table-actions-trigger]:ms-auto"
				columns={columns}
				data={rows}
				getRowId={getMemberId}
				rowActions={rowActions}
				sorting={{
					manual: true,
					sortState,
					onSortingChange: ([sorted]) =>
						onSearchChange({sortOrder: sorted?.desc ? 'DESC' : undefined, page: undefined}),
				}}
				pagination={{
					manual: true,
					pageSizes: [...PAGE_SIZES],
					defaultPageSize: DEFAULT_PAGE_SIZE,
					pageSize: search.pageSize ?? DEFAULT_PAGE_SIZE,
					pageIndex: (search.page ?? 1) - 1,
					rowCount: totalItems,
					onPaginationChange: ({pageIndex, pageSize}) => {
						const hasPageSizeChanged = pageSize !== (search.pageSize ?? DEFAULT_PAGE_SIZE);
						onSearchChange({
							pageSize: pageSize === DEFAULT_PAGE_SIZE ? undefined : (pageSize as TenantDetailSearch['pageSize']),
							page: hasPageSizeChanged || pageIndex === 0 ? undefined : pageIndex + 1,
						});
					},
				}}
				emptyState={t(`admin.tenants.empty_${tab}`)}
			/>
			<AssignMembersModal
				tab={tab}
				tenantId={tenantId}
				isOpen={isAssigning}
				isFreeText={isFreeText}
				assignedIds={rows.map(getMemberId)}
				onClose={() => setIsAssigning(false)}
			/>
			<UnassignMemberModal
				tab={tab}
				tenantId={tenantId}
				memberId={unassigningId}
				onClose={() => setUnassigningId(null)}
			/>
		</div>
	);
}

export {TenantMembersTab};
