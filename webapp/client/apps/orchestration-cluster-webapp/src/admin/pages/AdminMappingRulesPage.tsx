/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo, useState} from 'react';
import {useDebouncedUrlFilter} from '#/shared/hooks/useDebouncedUrlFilter';
import {useTranslation} from 'react-i18next';
import {Pencil, Plus, Trash2} from '@camunda/design-system/icons';
import {
	Button,
	DataTable,
	Label,
	PageHeader,
	PageLayout,
	SearchInput,
	type DataTableColumn,
	type SortingConfig,
} from '@camunda/design-system';
import type {MappingRule} from '@camunda/camunda-api-zod-schemas/8.11';
import {AddMappingRuleModal} from '#/admin/modules/mapping-rules/AddMappingRuleModal';
import {EditMappingRuleModal} from '#/admin/modules/mapping-rules/EditMappingRuleModal';
import {DeleteMappingRuleModal} from '#/admin/modules/mapping-rules/DeleteMappingRuleModal';
import {DEFAULT_PAGE_SIZE, PAGE_SIZES, type MappingRulesSearch} from '#/admin/modules/mapping-rules/searchSchema';

type SortingState = NonNullable<SortingConfig['sortState']>;

type ModalState = {type: 'add'} | {type: 'edit'; mappingRule: MappingRule} | {type: 'delete'; mappingRule: MappingRule};

const MAPPING_RULES_GUIDE_URL = 'https://docs.camunda.io/docs/next/components/admin/mapping-rules/';

type AdminMappingRulesPageProps = {
	mappingRules: MappingRule[];
	totalItems: number;
	search: MappingRulesSearch;
	onSearchChange: (next: Partial<MappingRulesSearch>) => void;
};

const AdminMappingRulesPage: React.FC<AdminMappingRulesPageProps> = ({
	mappingRules,
	totalItems,
	search,
	onSearchChange,
}) => {
	const {t} = useTranslation();
	const [modal, setModal] = useState<ModalState | null>(null);
	const appliedSearchTerm = search.search ?? '';
	const [searchDraft, setSearchDraft] = useDebouncedUrlFilter(appliedSearchTerm, (search) =>
		onSearchChange({search, page: undefined}),
	);

	const columns = useMemo<DataTableColumn<MappingRule>[]>(
		() => [
			{accessorKey: 'mappingRuleId', header: t('admin.mappingRules.mappingRuleIdColumn')},
			{accessorKey: 'name', header: t('admin.mappingRules.mappingRuleNameColumn')},
			{accessorKey: 'claimName', header: t('admin.mappingRules.claimNameColumn')},
			{accessorKey: 'claimValue', header: t('admin.mappingRules.claimValueColumn')},
		],
		[t],
	);

	const sortState = useMemo<SortingState>(
		() => [{id: search.sortField ?? 'mappingRuleId', desc: search.sortOrder === 'desc'}],
		[search.sortField, search.sortOrder],
	);

	const handleSortingChange = useCallback(
		(state: SortingState) => {
			const [sorted] = state;
			onSearchChange({
				sortField: sorted?.id as MappingRulesSearch['sortField'],
				sortOrder: sorted === undefined ? undefined : sorted.desc ? 'desc' : 'asc',
				page: undefined,
			});
		},
		[onSearchChange],
	);

	const handlePaginationChange = useCallback(
		({pageIndex, pageSize}: {pageIndex: number; pageSize: number}) => {
			const nextPageSize = pageSize === DEFAULT_PAGE_SIZE ? undefined : (pageSize as MappingRulesSearch['pageSize']);
			const hasPageSizeChanged = pageSize !== (search.pageSize ?? DEFAULT_PAGE_SIZE);
			onSearchChange({
				pageSize: nextPageSize,
				page: hasPageSizeChanged || pageIndex === 0 ? undefined : pageIndex + 1,
			});
		},
		[onSearchChange, search.pageSize],
	);

	const rowActions = useMemo(
		() => [
			{
				id: 'edit',
				label: t('admin.mappingRules.editMappingRule'),
				icon: <Pencil aria-hidden />,
				onClick: (mappingRule: MappingRule) => setModal({type: 'edit', mappingRule}),
			},
			{
				id: 'delete',
				label: t('admin.mappingRules.deleteMappingRule'),
				icon: <Trash2 aria-hidden />,
				variant: 'destructive' as const,
				onClick: (mappingRule: MappingRule) => setModal({type: 'delete', mappingRule}),
			},
		],
		[t],
	);

	const title = t('admin.headerNavItemMappingRules');

	return (
		<PageLayout id="main-content" tabIndex={-1}>
			<div className="flex flex-col gap-6">
				<div className="flex flex-col gap-1">
					<PageHeader title={title} />
					<p className="text-sm leading-5 text-muted-foreground">
						{t('admin.mappingRules.guideBody')}
						<Button asChild variant="link" className="h-auto p-0 align-baseline font-normal">
							<a href={MAPPING_RULES_GUIDE_URL} target="_blank" rel="noopener noreferrer">
								{t('admin.mappingRules.guideLinkLabel')}
							</a>
						</Button>
					</p>
				</div>
				<div className="flex flex-col gap-4">
					<div className="flex items-end justify-between gap-4">
						<div className="flex flex-1 flex-col gap-2">
							<Label htmlFor="mapping-rules-search" className="sr-only">
								{t('admin.mappingRules.searchByMappingRuleId')}
							</Label>
							<SearchInput
								id="mapping-rules-search"
								className="max-w-sm min-w-48"
								placeholder={t('admin.mappingRules.searchByMappingRuleId')}
								value={searchDraft}
								onChange={(event) => setSearchDraft(event.target.value)}
								onClear={() => setSearchDraft('')}
							/>
						</div>
						<Button onClick={() => setModal({type: 'add'})}>
							<Plus aria-hidden />
							{t('admin.mappingRules.addMappingRule')}
						</Button>
					</div>
					<DataTable
						aria-label={title}
						columns={columns}
						data={mappingRules}
						getRowId={(mappingRule) => mappingRule.mappingRuleId}
						rowActions={rowActions}
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
						emptyState={t('admin.mappingRules.noMappingRules')}
					/>
				</div>
			</div>
			<AddMappingRuleModal isOpen={modal?.type === 'add'} onClose={() => setModal(null)} />
			<EditMappingRuleModal
				mappingRule={modal?.type === 'edit' ? modal.mappingRule : null}
				onClose={() => setModal(null)}
			/>
			<DeleteMappingRuleModal
				mappingRule={modal?.type === 'delete' ? modal.mappingRule : null}
				onClose={() => setModal(null)}
			/>
		</PageLayout>
	);
};

export {AdminMappingRulesPage};
export type {AdminMappingRulesPageProps};
