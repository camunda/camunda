/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo, useState} from 'react';
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
	type DataTableRowAction,
	type SortingConfig,
} from '@camunda/design-system';
import type {Tenant} from '@camunda/camunda-api-zod-schemas/8.11';
import {AddTenantModal} from '#/admin/modules/tenants/AddTenantModal';
import {EditTenantModal} from '#/admin/modules/tenants/EditTenantModal';
import {DeleteTenantModal} from '#/admin/modules/tenants/DeleteTenantModal';
import {isDefaultTenant} from '#/admin/modules/tenants/defaultTenant';
import {DEFAULT_PAGE_SIZE, PAGE_SIZES, type TenantsSearch} from '#/admin/modules/tenants/searchSchema';
import {RouterRowLink} from '#/admin/modules/shared/RouterRowLink';
import {useAdminDetailPath} from '#/admin/modules/shared/useAdminDetailPath';
import {useDebouncedUrlFilter} from '#/shared/hooks/useDebouncedUrlFilter';

type SortingState = NonNullable<SortingConfig['sortState']>;

type ModalState = {type: 'add'} | {type: 'edit'; tenant: Tenant} | {type: 'delete'; tenant: Tenant};

const TENANTS_GUIDE_URL = 'https://docs.camunda.io/docs/next/components/admin/tenant/';

type AdminTenantsPageProps = {
	tenants: Tenant[];
	totalItems: number;
	search: TenantsSearch;
	onSearchChange: (next: Partial<TenantsSearch>) => void;
};

const AdminTenantsPage: React.FC<AdminTenantsPageProps> = ({tenants, totalItems, search, onSearchChange}) => {
	const detailPath = useAdminDetailPath();
	const {t} = useTranslation();
	const [modal, setModal] = useState<ModalState | null>(null);
	const appliedSearchTerm = search.search ?? '';
	const [searchDraft, setSearchDraft] = useDebouncedUrlFilter(appliedSearchTerm, (search) =>
		onSearchChange({search, page: undefined}),
	);

	const columns = useMemo<DataTableColumn<Tenant>[]>(
		() => [
			{accessorKey: 'tenantId', header: t('admin.tenants.tenantIdColumn')},
			{accessorKey: 'name', header: t('admin.tenants.tenantNameColumn')},
		],
		[t],
	);

	const sortState = useMemo<SortingState>(
		() => [{id: search.sortField ?? 'tenantId', desc: search.sortOrder === 'DESC'}],
		[search.sortField, search.sortOrder],
	);

	const handleSortingChange = useCallback(
		(state: SortingState) => {
			const [sorted] = state;
			onSearchChange({
				sortField: sorted?.id as TenantsSearch['sortField'],
				sortOrder: sorted === undefined ? undefined : sorted.desc ? 'DESC' : 'ASC',
				page: undefined,
			});
		},
		[onSearchChange],
	);

	const handlePaginationChange = useCallback(
		({pageIndex, pageSize}: {pageIndex: number; pageSize: number}) => {
			const nextPageSize = pageSize === DEFAULT_PAGE_SIZE ? undefined : (pageSize as TenantsSearch['pageSize']);
			const hasPageSizeChanged = pageSize !== (search.pageSize ?? DEFAULT_PAGE_SIZE);
			onSearchChange({
				pageSize: nextPageSize,
				page: hasPageSizeChanged || pageIndex === 0 ? undefined : pageIndex + 1,
			});
		},
		[onSearchChange, search.pageSize],
	);

	const rowActions = useMemo<DataTableRowAction<Tenant>[]>(
		() => [
			{
				id: 'edit',
				label: t('admin.tenants.editTenant'),
				icon: <Pencil aria-hidden />,
				visible: (tenant) => !isDefaultTenant(tenant.tenantId),
				onClick: (tenant) => setModal({type: 'edit', tenant}),
			},
			{
				id: 'delete',
				label: t('admin.tenants.deleteTenant'),
				icon: <Trash2 aria-hidden />,
				variant: 'destructive',
				visible: (tenant) => !isDefaultTenant(tenant.tenantId),
				onClick: (tenant) => setModal({type: 'delete', tenant}),
			},
		],
		[t],
	);

	const closeModal = useCallback(() => setModal(null), []);
	const title = t('admin.headerNavItemTenants');

	return (
		<PageLayout id="main-content" tabIndex={-1}>
			<div className="flex flex-col gap-6">
				<div className="flex flex-col gap-1">
					<PageHeader title={title} />
					<p className="text-sm leading-5 text-muted-foreground">
						{t('admin.tenants.guideBody')}
						<Button asChild variant="link" className="h-auto p-0 align-baseline font-normal">
							<a href={TENANTS_GUIDE_URL} target="_blank" rel="noopener noreferrer">
								{t('admin.tenants.guideLinkLabel')}
							</a>
						</Button>
					</p>
				</div>
				<div className="flex flex-col gap-4">
					<div className="flex items-end justify-between gap-4">
						<div className="flex flex-1 flex-col gap-2">
							<Label htmlFor="tenants-search" className="sr-only">
								{t('admin.tenants.searchByTenantId')}
							</Label>
							<SearchInput
								id="tenants-search"
								className="max-w-sm min-w-48"
								placeholder={t('admin.tenants.searchByTenantId')}
								value={searchDraft}
								onChange={(event) => setSearchDraft(event.target.value)}
								onClear={() => setSearchDraft('')}
							/>
						</div>
						<Button onClick={() => setModal({type: 'add'})}>
							<Plus aria-hidden />
							{t('admin.tenants.createTenant')}
						</Button>
					</div>
					<DataTable
						aria-label={title}
						className="**:data-[slot=data-table-actions-trigger]:ms-auto"
						columns={columns}
						data={tenants}
						getRowId={(tenant) => tenant.tenantId}
						rowHref={(tenant) => detailPath.tenant(tenant.tenantId)}
						linkComponent={RouterRowLink}
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
						emptyState={t('admin.tenants.noTenants')}
					/>
				</div>
			</div>
			<AddTenantModal isOpen={modal?.type === 'add'} onClose={closeModal} />
			<EditTenantModal tenant={modal?.type === 'edit' ? modal.tenant : null} onClose={closeModal} />
			<DeleteTenantModal tenant={modal?.type === 'delete' ? modal.tenant : null} onClose={closeModal} />
		</PageLayout>
	);
};

export {AdminTenantsPage};
export type {AdminTenantsPageProps};
