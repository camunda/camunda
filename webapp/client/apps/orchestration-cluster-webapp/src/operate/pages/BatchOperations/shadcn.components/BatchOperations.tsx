/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {OPERATE_BASE} from '#/shared/operateBase';
import {useEffect, useMemo} from 'react';
import {keepPreviousData, useQuery} from '@tanstack/react-query';
import {createLink, useNavigate} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {Alert, Button, Heading, Link, PageLayout, Pagination} from '@camunda/design-system';
import type {BatchOperation} from '@camunda/camunda-api-zod-schemas/8.11';
import {SortableTable} from '#/operate/shared/SortableTable/shadcn.components/SortableTable';
import {BatchItemsCount} from '#/operate/shared/BatchItemsCount/shadcn.components/BatchItemsCount';
import {BatchStateIndicator} from '#/operate/shared/BatchStateIndicator/shadcn.components/BatchStateIndicator';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/shadcn.components/EmptyMessage';
import {ForbiddenError} from '#/shared/errors';
import {ForbiddenPage} from '#/shared/pages/shadcn.components/ForbiddenPage';
import {GenericErrorPage} from '#/shared/pages/shadcn.components/GenericErrorPage';
import {batchOperationsOptions} from '../batchOperations.queries';
import {formatOperationType, formatStartDate} from '../utils';

type Props = {
	page: number;
	pageSize: number;
	sort: string;
};

const PAGE_SIZES = [20, 50, 100];
const DEFAULT_PAGE_SIZE = 20;

const TABLE_CONTAINER_CLASSES = [
	'[&_[data-slot=table-container]]:rounded-none! [&_[data-slot=table-container]]:border-0! [&_[data-slot=table-container]]:shadow-none!',
	'[&_[data-slot=data-table]>div.rounded-xl]:rounded-none! [&_[data-slot=data-table]>div.rounded-xl]:border-0!',
].join(' ');

const TableLink: React.FC<React.ComponentProps<'a'>> = (props) => <Link {...props} />;
const BatchOperationLink = createLink(TableLink);

const BatchOperations: React.FC<Props> = ({page, pageSize, sort}) => {
	const navigate = useNavigate();
	const {t, i18n} = useTranslation();
	const options = useMemo(() => batchOperationsOptions({page, pageSize, sort}), [page, pageSize, sort]);
	const query = useQuery({...options, placeholderData: keepPreviousData});

	useEffect(() => {
		const updateTitle = () => {
			document.title = i18n.t('operate.batchOperations.pageTitle');
		};
		updateTitle();
		i18n.on('languageChanged', updateTitle);
		return () => {
			i18n.off('languageChanged', updateTitle);
		};
	}, [i18n]);

	const columns = useMemo(
		() => [
			{
				key: 'operationType',
				label: t('operate.batchOperations.operation'),
				sortKey: 'operationType',
				defaultOrder: 'DESC' as const,
				render: (row: BatchOperation) => (
					<BatchOperationLink
						to={`${OPERATE_BASE}/batch-operations/$batchOperationKey` as const}
						params={{batchOperationKey: row.batchOperationKey}}
					>
						{formatOperationType(row.batchOperationType)}
					</BatchOperationLink>
				),
			},
			{
				key: 'state',
				label: t('operate.batchOperations.state'),
				sortKey: 'state',
				defaultOrder: 'DESC' as const,
				render: (row: BatchOperation) => <BatchStateIndicator state={row.state} />,
			},
			{
				key: 'items',
				label: t('operate.batchOperations.items'),
				render: (row: BatchOperation) => (
					<BatchItemsCount
						totalCount={row.operationsTotalCount}
						completedCount={row.operationsCompletedCount}
						failedCount={row.operationsFailedCount}
					/>
				),
			},
			{
				key: 'actor',
				label: t('operate.batchOperations.actor'),
				sortKey: 'actorId',
				defaultOrder: 'DESC' as const,
				render: (row: BatchOperation) => row.actorId ?? '--',
			},
			{
				key: 'startDate',
				label: t('operate.batchOperations.startDate'),
				sortKey: 'startDate',
				defaultOrder: 'DESC' as const,
				render: (row: BatchOperation) => formatStartDate(row.startDate),
			},
		],
		[t],
	);

	if (query.error instanceof ForbiddenError) {
		return (
			<PageLayout id="main-content" tabIndex={-1} padding="none" width="full" className="h-full">
				<ForbiddenPage />
			</PageLayout>
		);
	}

	if (query.isError && query.data === undefined) {
		return (
			<PageLayout id="main-content" tabIndex={-1} padding="none" width="full" className="h-full">
				<GenericErrorPage reset={() => void query.refetch()} />
			</PageLayout>
		);
	}

	const rows = query.data?.items ?? [];
	const isLoading = query.isFetching || query.isPending;
	const totalItems = query.data?.page.totalItems ?? 0;
	const displayPageSize = PAGE_SIZES.includes(pageSize) ? pageSize : DEFAULT_PAGE_SIZE;
	const pageCount = Math.ceil(totalItems / displayPageSize);
	const rangeStart = Math.min((page - 1) * displayPageSize + 1, totalItems);
	const rangeEnd = Math.min(page * displayPageSize, totalItems);
	const pageOfLabel = [
		t('operate.batchOperations.itemRange', {min: rangeStart, max: rangeEnd, count: totalItems}),
		page <= pageCount ? t('operate.batchOperations.pageOf', {page, count: pageCount}) : null,
	]
		.filter(Boolean)
		.join(' · ');

	let emptyState: React.ReactNode;
	if (!isLoading) {
		emptyState = query.isError ? (
			<></>
		) : (
			<EmptyMessage
				message={t('operate.batchOperations.emptyMessage')}
				additionalInfo={t('operate.batchOperations.emptyAdditionalInfo')}
			/>
		);
	}

	return (
		<PageLayout id="main-content" tabIndex={-1} padding="none" width="full" className="h-full">
			<div className="flex h-full min-h-0 flex-col overflow-hidden">
				<Heading as="h1" variant="heading-lg" className="m-0 px-6 pt-6 pb-4">
					{t('operate.batchOperations.title')}
				</Heading>
				{query.isError && (
					<div className="flex items-center gap-2 px-4 pb-2">
						<Alert
							variant="destructive"
							role="alert"
							title={t('operate.batchOperations.refreshError')}
							className="flex-1"
						/>
						<Button variant="ghost" onClick={() => void query.refetch()}>
							{t('errorGenericErrorPageButtonLabel')}
						</Button>
					</div>
				)}
				<div className="min-h-0 flex-1 overflow-auto px-4">
					<SortableTable<BatchOperation>
						columns={columns}
						rows={rows}
						rowKey={(row) => row.batchOperationKey}
						ariaLabel={t('operate.batchOperations.title')}
						loadingPreviousPageLabel={t('operate.batchOperations.previousPage')}
						loadingNextPageLabel={t('operate.batchOperations.nextPage')}
						isFetching={isLoading}
						emptyState={emptyState}
						hideHeaderWhenEmpty={!isLoading && !query.isError}
						className={TABLE_CONTAINER_CLASSES}
						data-testid="batch-operations-table"
					/>
				</div>
				{totalItems > pageSize && (
					<Pagination
						page={page}
						pageCount={pageCount}
						pageOfLabel={pageOfLabel}
						pageSize={displayPageSize}
						pageSizes={PAGE_SIZES}
						rowsPerPageLabel={t('operate.batchOperations.itemsPerPage')}
						previousPageLabel={t('operate.batchOperations.previousPage')}
						nextPageLabel={t('operate.batchOperations.nextPage')}
						onPageChange={(newPage) => {
							void navigate({to: '.', search: (prev) => ({...prev, page: newPage, pageSize: displayPageSize})});
						}}
						onPageSizeChange={(newPageSize) => {
							void navigate({to: '.', search: (prev) => ({...prev, page: 1, pageSize: newPageSize})});
						}}
					/>
				)}
			</div>
		</PageLayout>
	);
};

export {BatchOperations};
