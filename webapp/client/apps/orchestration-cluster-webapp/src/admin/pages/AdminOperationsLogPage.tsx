/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {useDebouncedUrlFilter} from '#/shared/hooks/useDebouncedUrlFilter';
import {useTranslation} from 'react-i18next';
import type {TFunction} from 'i18next';
import {endOfDay} from 'date-fns';
import {
	Button,
	Card,
	CardContent,
	CardHeader,
	CardTitle,
	DataTable,
	DateRangePicker,
	Input,
	Label,
	PageContentLayout,
	PageHeader,
	PageLayout,
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
	StatusIcon,
	Text,
	type DataTableColumn,
	type DateRange,
	type SortingConfig,
} from '@camunda/design-system';
import {AiIcon, ExternalLink, Plug, User} from '@camunda/design-system/icons';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10';
import {formatEnumLabel} from '#/admin/modules/operations-log/auditLogs';
import {
	ALLOWED_ENTITY_TYPES,
	ALLOWED_OPERATION_TYPES,
	ALLOWED_RESULT_TYPES,
	DEFAULT_PAGE_SIZE,
	PAGE_SIZES,
	type OperationsLogSearch,
} from '#/admin/modules/operations-log/searchSchema';

const DOCS_URL = 'https://docs.camunda.io/docs/next/components/admin/audit-operations/';

type SortingState = NonNullable<SortingConfig['sortState']>;

const EMPTY_CELL = '-';
const ALL_OPTION = 'all';

const FILTER_FIELDS = [
	'operationType',
	'entityType',
	'relatedEntityType',
	'relatedEntityKey',
	'result',
	'actor',
	'timestampFrom',
	'timestampTo',
] as const satisfies readonly (keyof OperationsLogSearch)[];

type AdminOperationsLogPageProps = {
	auditLogs: AuditLog[];
	totalItems: number;
	search: OperationsLogSearch;
	onSearchChange: (next: Partial<OperationsLogSearch>) => void;
};

function getPropertyCell(log: AuditLog, t: TFunction): React.ReactNode {
	if (log.result === 'FAIL') {
		return (
			<div>
				<Text as="div" variant="label-sm" className="text-neutral-foreground-subtle">
					{t('admin.operationsLog.errorCode')}
				</Text>
				{log.entityDescription}
			</div>
		);
	}

	const isAuthorizationCreate = log.entityType === 'AUTHORIZATION' && log.operationType === 'CREATE';
	const isAssignOperation = log.operationType === 'ASSIGN' || log.operationType === 'UNASSIGN';

	if (!isAuthorizationCreate && !isAssignOperation) {
		return EMPTY_CELL;
	}

	return (
		<div>
			<Text as="div" variant="label-sm" className="text-neutral-foreground-subtle">
				{isAuthorizationCreate ? t('admin.operationsLog.owner') : t('admin.operationsLog.assignee')}
			</Text>
			<div className="whitespace-nowrap">
				{log.relatedEntityType ? formatEnumLabel(log.relatedEntityType) : EMPTY_CELL}{' '}
				<Text as="code" variant="code-sm">
					{log.relatedEntityKey}
				</Text>
			</div>
		</div>
	);
}

const AdminOperationsLogPage: React.FC<AdminOperationsLogPageProps> = ({
	auditLogs,
	totalItems,
	search,
	onSearchChange,
}) => {
	const {t} = useTranslation();
	const isAuthorizationEntity = search.entityType === 'AUTHORIZATION';

	const [actorDraft, setActorDraft] = useDebouncedUrlFilter(search.actor ?? '', (actor) =>
		onSearchChange({actor, page: undefined}),
	);
	const [ownerKeyDraft, setOwnerKeyDraft] = useDebouncedUrlFilter(search.relatedEntityKey ?? '', (relatedEntityKey) =>
		onSearchChange({relatedEntityKey, page: undefined}),
	);

	const handleEntityTypeChange = useCallback(
		(value: string) => {
			const entityType = value === ALL_OPTION ? undefined : (value as OperationsLogSearch['entityType']);
			const staysAuthorization = entityType === 'AUTHORIZATION';

			onSearchChange({
				entityType,
				relatedEntityType: staysAuthorization ? search.relatedEntityType : undefined,
				relatedEntityKey: staysAuthorization ? search.relatedEntityKey : undefined,
				page: undefined,
			});

			if (!staysAuthorization) {
				setOwnerKeyDraft('');
			}
		},
		[onSearchChange, search.relatedEntityKey, search.relatedEntityType, setOwnerKeyDraft],
	);

	const dateRange = useMemo<DateRange | undefined>(() => {
		if (search.timestampFrom === undefined && search.timestampTo === undefined) {
			return undefined;
		}

		return {
			from: search.timestampFrom === undefined ? undefined : new Date(search.timestampFrom),
			to: search.timestampTo === undefined ? undefined : new Date(search.timestampTo),
		};
	}, [search.timestampFrom, search.timestampTo]);

	const handleDateRangeChange = useCallback(
		(range: DateRange | undefined) => {
			onSearchChange({
				timestampFrom: range?.from?.toISOString(),
				// The picker returns midnight for the selected end date; use the end of that
				// day so the range includes logs recorded later on the same date.
				timestampTo: range?.to === undefined ? undefined : endOfDay(range.to).toISOString(),
				page: undefined,
			});
		},
		[onSearchChange],
	);

	const isFilterActive = FILTER_FIELDS.some((field) => search[field] !== undefined);

	const handleReset = useCallback(() => {
		onSearchChange({
			operationType: undefined,
			entityType: undefined,
			relatedEntityType: undefined,
			relatedEntityKey: undefined,
			result: undefined,
			actor: undefined,
			timestampFrom: undefined,
			timestampTo: undefined,
			page: undefined,
		});
		setActorDraft('');
		setOwnerKeyDraft('');
	}, [onSearchChange, setActorDraft, setOwnerKeyDraft]);

	const columns = useMemo<DataTableColumn<AuditLog>[]>(
		() => [
			{
				id: 'result',
				header: t('admin.operationsLog.status'),
				enableSorting: false,
				cell: ({row}) => (
					<StatusIcon
						size="lg"
						variant={row.original.result === 'SUCCESS' ? 'success' : 'danger'}
						label={formatEnumLabel(row.original.result)}
					/>
				),
			},
			{
				accessorKey: 'operationType',
				header: t('admin.operationsLog.operationType'),
				cell: ({row}) => formatEnumLabel(row.original.operationType),
			},
			{
				accessorKey: 'entityType',
				header: t('admin.operationsLog.entityType'),
				cell: ({row}) => formatEnumLabel(row.original.entityType),
			},
			{
				id: 'reference',
				header: t('admin.operationsLog.reference'),
				enableSorting: false,
				cell: ({row}) => (
					<Text as="code" variant="code-sm">
						{row.original.entityDescription?.trim() || row.original.entityKey}
					</Text>
				),
			},
			{
				id: 'property',
				header: t('admin.operationsLog.property'),
				enableSorting: false,
				cell: ({row}) => getPropertyCell(row.original, t),
			},
			{
				accessorKey: 'actorId',
				header: t('admin.operationsLog.actor'),
				cell: ({row}) => {
					const log = row.original;

					if (!log.actorId) {
						return EMPTY_CELL;
					}

					const isClient = log.actorType === 'CLIENT';
					const ActorIcon = isClient ? Plug : User;
					const actorLabel = isClient
						? t('admin.operationsLog.actorTypeClient')
						: t('admin.operationsLog.actorTypeUser');

					return (
						<div className="flex items-center gap-1">
							<ActorIcon role="img" aria-label={actorLabel} className="h-4 w-4" />
							{log.agentElementId && (
								<AiIcon role="img" aria-label={t('admin.operationsLog.actorTypeAgent')} className="h-4 w-4" />
							)}
							{log.actorId}
						</div>
					);
				},
			},
			{
				accessorKey: 'timestamp',
				header: t('admin.operationsLog.date'),
				cell: ({row}) => new Date(row.original.timestamp).toLocaleString(),
			},
		],
		[t],
	);

	const sortState = useMemo<SortingState>(
		() =>
			search.sortField === undefined
				? [{id: 'timestamp', desc: true}]
				: [{id: search.sortField, desc: search.sortOrder === 'desc'}],
		[search.sortField, search.sortOrder],
	);

	const handleSortingChange = useCallback(
		(state: SortingState) => {
			const [sorted] = state;

			onSearchChange({
				sortField: sorted?.id as OperationsLogSearch['sortField'],
				sortOrder: sorted === undefined ? undefined : sorted.desc ? 'desc' : 'asc',
				page: undefined,
			});
		},
		[onSearchChange],
	);

	const handlePaginationChange = useCallback(
		({pageIndex, pageSize}: {pageIndex: number; pageSize: number}) => {
			const nextPageSize = pageSize === DEFAULT_PAGE_SIZE ? undefined : (pageSize as OperationsLogSearch['pageSize']);
			const hasPageSizeChanged = pageSize !== (search.pageSize ?? DEFAULT_PAGE_SIZE);

			onSearchChange({
				pageSize: nextPageSize,
				page: hasPageSizeChanged || pageIndex === 0 ? undefined : pageIndex + 1,
			});
		},
		[onSearchChange, search.pageSize],
	);

	const title = t('admin.headerNavItemOperationsLog');

	return (
		<PageLayout id="main-content" tabIndex={-1}>
			<div className="flex flex-col gap-6">
				<div className="flex flex-col gap-1">
					<PageHeader title={title} />
					<p className="text-sm leading-5 text-muted-foreground">
						{t('admin.operationsLog.guideBody')}
						<Button asChild variant="link" className="h-auto p-0 align-baseline font-normal">
							<a href={DOCS_URL} target="_blank" rel="noopener noreferrer">
								{t('admin.operationsLog.guideLinkLabel')}
								<ExternalLink aria-hidden />
							</a>
						</Button>
					</p>
				</div>

				<PageContentLayout
					sidebarPosition="left"
					sidebarWidth={240}
					sidebar={
						<Card className="sticky top-0">
							<CardHeader>
								<CardTitle>{t('admin.operationsLog.filter')}</CardTitle>
							</CardHeader>
							<CardContent className="flex flex-col gap-4">
								<div className="flex flex-col gap-1">
									<Label htmlFor="operations-log-operation-type">{t('admin.operationsLog.operationType')}</Label>
									<Select
										value={search.operationType ?? ALL_OPTION}
										onValueChange={(value) =>
											onSearchChange({
												operationType:
													value === ALL_OPTION ? undefined : (value as OperationsLogSearch['operationType']),
												page: undefined,
											})
										}
									>
										<SelectTrigger id="operations-log-operation-type" className="w-full">
											<SelectValue placeholder={t('admin.operationsLog.operationType')} />
										</SelectTrigger>
										<SelectContent>
											<SelectItem value={ALL_OPTION}>{t('admin.operationsLog.all')}</SelectItem>
											{ALLOWED_OPERATION_TYPES.map((operationType) => (
												<SelectItem key={operationType} value={operationType}>
													{formatEnumLabel(operationType)}
												</SelectItem>
											))}
										</SelectContent>
									</Select>
								</div>

								<div className="flex flex-col gap-1">
									<Label htmlFor="operations-log-entity-type">{t('admin.operationsLog.entityType')}</Label>
									<Select value={search.entityType ?? ALL_OPTION} onValueChange={handleEntityTypeChange}>
										<SelectTrigger id="operations-log-entity-type" className="w-full">
											<SelectValue placeholder={t('admin.operationsLog.entityType')} />
										</SelectTrigger>
										<SelectContent>
											<SelectItem value={ALL_OPTION}>{t('admin.operationsLog.all')}</SelectItem>
											{ALLOWED_ENTITY_TYPES.map((entityType) => (
												<SelectItem key={entityType} value={entityType}>
													{formatEnumLabel(entityType)}
												</SelectItem>
											))}
										</SelectContent>
									</Select>
								</div>

								{isAuthorizationEntity && (
									<>
										<div className="flex flex-col gap-1">
											<Label htmlFor="operations-log-related-entity-type">{t('admin.operationsLog.ownerType')}</Label>
											<Select
												value={search.relatedEntityType ?? ALL_OPTION}
												onValueChange={(value) =>
													onSearchChange({
														relatedEntityType:
															value === ALL_OPTION ? undefined : (value as OperationsLogSearch['relatedEntityType']),
														page: undefined,
													})
												}
											>
												<SelectTrigger id="operations-log-related-entity-type" className="w-full">
													<SelectValue placeholder={t('admin.operationsLog.ownerType')} />
												</SelectTrigger>
												<SelectContent>
													<SelectItem value={ALL_OPTION}>{t('admin.operationsLog.all')}</SelectItem>
													{ALLOWED_ENTITY_TYPES.map((entityType) => (
														<SelectItem key={entityType} value={entityType}>
															{formatEnumLabel(entityType)}
														</SelectItem>
													))}
												</SelectContent>
											</Select>
										</div>

										<div className="flex flex-col gap-1">
											<Label htmlFor="operations-log-owner-key">{t('admin.operationsLog.ownerKey')}</Label>
											<Input
												id="operations-log-owner-key"
												className="w-full"
												placeholder={t('admin.operationsLog.ownerKeyPlaceholder')}
												value={ownerKeyDraft}
												onChange={(event) => setOwnerKeyDraft(event.target.value)}
											/>
										</div>
									</>
								)}

								<div className="flex flex-col gap-1">
									<Label htmlFor="operations-log-result">{t('admin.operationsLog.status')}</Label>
									<Select
										value={search.result ?? ALL_OPTION}
										onValueChange={(value) =>
											onSearchChange({
												result: value === ALL_OPTION ? undefined : (value as OperationsLogSearch['result']),
												page: undefined,
											})
										}
									>
										<SelectTrigger id="operations-log-result" className="w-full">
											<SelectValue placeholder={t('admin.operationsLog.status')} />
										</SelectTrigger>
										<SelectContent>
											<SelectItem value={ALL_OPTION}>{t('admin.operationsLog.all')}</SelectItem>
											{ALLOWED_RESULT_TYPES.map((result) => (
												<SelectItem key={result} value={result}>
													{formatEnumLabel(result)}
												</SelectItem>
											))}
										</SelectContent>
									</Select>
								</div>

								<div className="flex flex-col gap-1">
									<Label htmlFor="operations-log-actor">{t('admin.operationsLog.actor')}</Label>
									<Input
										id="operations-log-actor"
										className="w-full"
										placeholder={t('admin.operationsLog.actorPlaceholder')}
										value={actorDraft}
										onChange={(event) => setActorDraft(event.target.value)}
									/>
								</div>

								<div className="flex flex-col gap-1">
									<Label htmlFor="operations-log-date-range">{t('admin.operationsLog.date')}</Label>
									<DateRangePicker
										id="operations-log-date-range"
										className="w-full"
										value={dateRange}
										onChange={handleDateRangeChange}
									/>
								</div>

								<Button
									className="mx-auto w-fit"
									type="reset"
									variant="ghost"
									size="sm"
									disabled={!isFilterActive}
									onClick={handleReset}
								>
									{t('admin.operationsLog.reset')}
								</Button>
							</CardContent>
						</Card>
					}
				>
					<DataTable
						aria-label={title}
						columns={columns}
						data={auditLogs}
						getRowId={(log) => log.auditLogKey}
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
						emptyState={t('admin.operationsLog.noOperations')}
					/>
				</PageContentLayout>
			</div>
		</PageLayout>
	);
};

export {AdminOperationsLogPage};
export type {AdminOperationsLogPageProps};
