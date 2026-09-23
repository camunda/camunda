/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {
	DataTable,
	Label,
	PageHeader,
	PageLayout,
	SearchInput,
	type DataTableColumn,
	type SortingConfig,
} from '@camunda/design-system';
import {ExpandedToolDetails} from '#/admin/modules/mcp-processes/ExpandedToolDetails';
import type {McpProcessTool} from '#/admin/modules/mcp-processes/mcpProcessTools';
import {DEFAULT_PAGE_SIZE, PAGE_SIZES, type McpProcessesSearch} from '#/admin/modules/mcp-processes/searchSchema';

type SortingState = NonNullable<SortingConfig['sortState']>;

const SEARCH_DEBOUNCE = 500;
const EMPTY_CELL = '-';
const SORTED_COLUMN_ID = 'toolName';

type AdminMcpProcessesPageProps = {
	tools: McpProcessTool[];
	totalItems: number;
	search: McpProcessesSearch;
	isTenantsApiEnabled: boolean;
	onSearchChange: (next: Partial<McpProcessesSearch>) => void;
};

const AdminMcpProcessesPage: React.FC<AdminMcpProcessesPageProps> = ({
	tools,
	totalItems,
	search,
	isTenantsApiEnabled,
	onSearchChange,
}) => {
	const {t} = useTranslation();
	const appliedSearchTerm = search.search ?? '';
	const [searchDraft, setSearchDraft] = useState(appliedSearchTerm);
	const [lastAppliedSearchTerm, setLastAppliedSearchTerm] = useState(appliedSearchTerm);

	// Back/forward navigation changes the applied term without touching the draft, which
	// would otherwise leave the input showing a term the table is no longer filtered by.
	if (lastAppliedSearchTerm !== appliedSearchTerm) {
		setLastAppliedSearchTerm(appliedSearchTerm);
		setSearchDraft(appliedSearchTerm);
	}

	useEffect(() => {
		if (searchDraft === appliedSearchTerm) {
			return;
		}

		const timeoutId = setTimeout(() => {
			onSearchChange({search: searchDraft === '' ? undefined : searchDraft, page: undefined});
		}, SEARCH_DEBOUNCE);

		return () => clearTimeout(timeoutId);
	}, [appliedSearchTerm, onSearchChange, searchDraft]);

	const columns = useMemo<DataTableColumn<McpProcessTool>[]>(() => {
		const visibleColumns: DataTableColumn<McpProcessTool>[] = [
			{
				accessorKey: SORTED_COLUMN_ID,
				header: t('admin.mcpProcesses.toolName'),
			},
			{
				id: 'toolDescription',
				header: t('admin.mcpProcesses.toolDescription'),
				cell: ({row}) => row.original.toolProperties.purpose ?? EMPTY_CELL,
				enableSorting: false,
			},
			{
				accessorKey: 'processDefinitionName',
				header: t('admin.mcpProcesses.processName'),
				enableSorting: false,
			},
			{
				id: 'processDefinitionVersion',
				header: t('admin.mcpProcesses.version'),
				cell: ({row}) => row.original.processDefinitionVersion ?? EMPTY_CELL,
				enableSorting: false,
			},
		];

		if (isTenantsApiEnabled) {
			visibleColumns.push({
				accessorKey: 'tenantId',
				header: t('admin.mcpProcesses.tenant'),
				enableSorting: false,
			});
		}

		return visibleColumns;
	}, [isTenantsApiEnabled, t]);

	const sortState = useMemo<SortingState>(
		() => [{id: SORTED_COLUMN_ID, desc: search.sortOrder === 'desc'}],
		[search.sortOrder],
	);

	const handleSortingChange = useCallback(
		(state: SortingState) => {
			const sortedColumn = state.find(({id}) => id === SORTED_COLUMN_ID);

			onSearchChange({
				sortOrder: sortedColumn === undefined || !sortedColumn.desc ? undefined : 'desc',
				page: undefined,
			});
		},
		[onSearchChange],
	);

	const handlePaginationChange = useCallback(
		({pageIndex, pageSize}: {pageIndex: number; pageSize: number}) => {
			const nextPageSize = pageSize === DEFAULT_PAGE_SIZE ? undefined : (pageSize as McpProcessesSearch['pageSize']);
			const hasPageSizeChanged = pageSize !== (search.pageSize ?? DEFAULT_PAGE_SIZE);

			onSearchChange({
				pageSize: nextPageSize,
				page: hasPageSizeChanged || pageIndex === 0 ? undefined : pageIndex + 1,
			});
		},
		[onSearchChange, search.pageSize],
	);

	const renderExpandedRow = useCallback(
		(tool: McpProcessTool) => <ExpandedToolDetails toolProperties={tool.toolProperties} />,
		[],
	);

	const title = t('admin.headerNavItemMcpProcesses');

	return (
		<PageLayout id="main-content" tabIndex={-1}>
			<div className="flex flex-col gap-6">
				<PageHeader title={title} />

				<div className="flex flex-col gap-4">
					<Label htmlFor="mcp-processes-search" className="sr-only">
						{t('admin.mcpProcesses.searchByToolName')}
					</Label>
					<SearchInput
						id="mcp-processes-search"
						className="max-w-sm min-w-48"
						placeholder={t('admin.mcpProcesses.searchByToolName')}
						value={searchDraft}
						onChange={(event) => setSearchDraft(event.target.value)}
						onClear={() => setSearchDraft('')}
					/>
					<DataTable
						aria-label={title}
						columns={columns}
						data={tools}
						getRowId={(tool) => tool.id}
						expansion={renderExpandedRow}
						sorting={{manual: true, sortState, onSortingChange: handleSortingChange}}
						pagination={{
							manual: true,
							pageSizes: [...PAGE_SIZES],
							defaultPageSize: DEFAULT_PAGE_SIZE,
							pageSize: search.pageSize ?? DEFAULT_PAGE_SIZE,
							pageIndex: (search.page ?? 1) - 1,
							rowCount: totalItems,
							onPaginationChange: handlePaginationChange,
						}}
						emptyState={t('admin.mcpProcesses.noMcpProcesses')}
					/>
				</div>
			</div>
		</PageLayout>
	);
};

export {AdminMcpProcessesPage};
export type {AdminMcpProcessesPageProps};
