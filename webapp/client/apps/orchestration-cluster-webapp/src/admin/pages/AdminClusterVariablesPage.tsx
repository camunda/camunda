/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useMemo, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Eye, Pencil, Plus, Trash2} from '@camunda/design-system/icons';
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
import type {ClusterVariable} from '@camunda/camunda-api-zod-schemas/8.11';
import {AddClusterVariableModal} from '#/admin/modules/cluster-variables/AddClusterVariableModal';
import {DeleteClusterVariableModal} from '#/admin/modules/cluster-variables/DeleteClusterVariableModal';
import {EditClusterVariableModal} from '#/admin/modules/cluster-variables/EditClusterVariableModal';
import {ViewClusterVariableModal} from '#/admin/modules/cluster-variables/ViewClusterVariableModal';
import {
	DEFAULT_PAGE_SIZE,
	PAGE_SIZES,
	type ClusterVariablesSearch,
} from '#/admin/modules/cluster-variables/searchSchema';

type SortingState = NonNullable<SortingConfig['sortState']>;

type ModalState =
	| {type: 'add'}
	| {type: 'view'; clusterVariable: ClusterVariable}
	| {type: 'edit'; clusterVariable: ClusterVariable}
	| {type: 'delete'; clusterVariable: ClusterVariable};

const SEARCH_DEBOUNCE = 500;
const CLUSTER_VARIABLES_GUIDE_URL =
	'https://docs.camunda.io/docs/next/components/modeler/feel/cluster-variable/cluster-variable-overview/';

type AdminClusterVariablesPageProps = {
	clusterVariables: ClusterVariable[];
	totalItems: number;
	search: ClusterVariablesSearch;
	isTenantScopeAvailable: boolean;
	onSearchChange: (next: Partial<ClusterVariablesSearch>) => void;
};

const AdminClusterVariablesPage: React.FC<AdminClusterVariablesPageProps> = ({
	clusterVariables,
	totalItems,
	search,
	isTenantScopeAvailable,
	onSearchChange,
}) => {
	const {t} = useTranslation();
	const [modal, setModal] = useState<ModalState | null>(null);
	const appliedSearchTerm = search.search ?? '';
	const [searchDraft, setSearchDraft] = useState(appliedSearchTerm);
	const [lastAppliedSearchTerm, setLastAppliedSearchTerm] = useState(appliedSearchTerm);

	// Back/forward navigation changes the applied term without touching the draft, which
	// would otherwise leave the input showing a term the table is no longer filtered by. Only
	// sync the draft in that case (draft still matches what was last applied) — otherwise a
	// slow round-trip for an earlier debounce would clobber input typed in the meantime.
	if (lastAppliedSearchTerm !== appliedSearchTerm) {
		if (searchDraft === lastAppliedSearchTerm) {
			setSearchDraft(appliedSearchTerm);
		}
		setLastAppliedSearchTerm(appliedSearchTerm);
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

	const columns = useMemo<DataTableColumn<ClusterVariable>[]>(
		() => [
			{accessorKey: 'name', header: t('admin.clusterVariables.nameColumn')},
			{
				accessorKey: 'value',
				header: t('admin.clusterVariables.valueColumn'),
				cell: ({row}) => <span className="block max-w-md truncate font-mono text-xs">{row.original.value}</span>,
				enableSorting: false,
			},
			{
				id: 'scope',
				header: t('admin.clusterVariables.scopeColumn'),
				accessorFn: ({scope, tenantId}) =>
					scope === 'TENANT'
						? t('admin.clusterVariables.scopeTenantWithId', {tenantId})
						: t('admin.clusterVariables.scopeGlobal'),
				enableSorting: false,
			},
		],
		[t],
	);

	const sortState = useMemo<SortingState>(() => [{id: 'name', desc: search.sortOrder === 'DESC'}], [search.sortOrder]);

	const handleSortingChange = useCallback(
		(state: SortingState) => {
			const [sorted] = state;
			onSearchChange({
				sortOrder: sorted === undefined ? undefined : sorted.desc ? 'DESC' : 'ASC',
				page: undefined,
			});
		},
		[onSearchChange],
	);

	const handlePaginationChange = useCallback(
		({pageIndex, pageSize}: {pageIndex: number; pageSize: number}) => {
			const nextPageSize =
				pageSize === DEFAULT_PAGE_SIZE ? undefined : (pageSize as ClusterVariablesSearch['pageSize']);
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
				id: 'view',
				label: t('admin.clusterVariables.viewClusterVariable'),
				icon: <Eye aria-hidden />,
				onClick: (clusterVariable: ClusterVariable) => setModal({type: 'view', clusterVariable}),
			},
			{
				id: 'edit',
				label: t('admin.clusterVariables.editClusterVariable'),
				icon: <Pencil aria-hidden />,
				onClick: (clusterVariable: ClusterVariable) => setModal({type: 'edit', clusterVariable}),
			},
			{
				id: 'delete',
				label: t('admin.clusterVariables.deleteClusterVariable'),
				icon: <Trash2 aria-hidden />,
				variant: 'destructive' as const,
				onClick: (clusterVariable: ClusterVariable) => setModal({type: 'delete', clusterVariable}),
			},
		],
		[t],
	);

	const title = t('admin.headerNavItemClusterVariables');
	const closeModal = () => setModal(null);

	return (
		<PageLayout id="main-content" tabIndex={-1}>
			<div className="flex flex-col gap-6">
				<div className="flex flex-col gap-1">
					<PageHeader title={title} />
					<p className="text-sm leading-5 text-muted-foreground">
						{t('admin.clusterVariables.guideBody')}
						<Button asChild variant="link" className="h-auto p-0 align-baseline font-normal">
							<a href={CLUSTER_VARIABLES_GUIDE_URL} target="_blank" rel="noopener noreferrer">
								{t('admin.clusterVariables.guideLinkLabel')}
							</a>
						</Button>
					</p>
				</div>
				<div className="flex flex-col gap-4">
					<div className="flex items-end justify-between gap-4">
						<div className="flex flex-1 flex-col gap-2">
							<Label htmlFor="cluster-variables-search" className="sr-only">
								{t('admin.clusterVariables.searchByName')}
							</Label>
							<SearchInput
								id="cluster-variables-search"
								className="max-w-sm min-w-48"
								placeholder={t('admin.clusterVariables.searchByName')}
								value={searchDraft}
								onChange={(event) => setSearchDraft(event.target.value)}
								onClear={() => setSearchDraft('')}
							/>
						</div>
						<Button onClick={() => setModal({type: 'add'})}>
							<Plus aria-hidden />
							{t('admin.clusterVariables.addClusterVariable')}
						</Button>
					</div>
					<DataTable
						aria-label={title}
						columns={columns}
						data={clusterVariables}
						getRowId={({scope, tenantId, name}) => `${scope}:${tenantId ?? ''}:${name}`}
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
						emptyState={t('admin.clusterVariables.noClusterVariables')}
					/>
				</div>
			</div>
			<AddClusterVariableModal
				isOpen={modal?.type === 'add'}
				isTenantScopeAvailable={isTenantScopeAvailable}
				onClose={closeModal}
			/>
			<ViewClusterVariableModal
				clusterVariable={modal?.type === 'view' ? modal.clusterVariable : null}
				onClose={closeModal}
			/>
			<EditClusterVariableModal
				clusterVariable={modal?.type === 'edit' ? modal.clusterVariable : null}
				onClose={closeModal}
			/>
			<DeleteClusterVariableModal
				clusterVariable={modal?.type === 'delete' ? modal.clusterVariable : null}
				onClose={closeModal}
			/>
		</PageLayout>
	);
};

export {AdminClusterVariablesPage};
export type {AdminClusterVariablesPageProps};
