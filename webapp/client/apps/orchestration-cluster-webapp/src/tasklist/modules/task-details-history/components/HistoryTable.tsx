/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {
	DataTable,
	Table,
	TableBody,
	TableCell,
	TableContainer,
	TableHead,
	TableRow,
	type DataTableHeader,
} from '@carbon/react';
import {Information} from '@carbon/react/icons';
import {Link} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10';
import {cn} from '#/shared/cn';
import {formatHistoryDate} from '../formatHistoryDate';
import {getOperationTypeTranslationKey} from '../getOperationTypeTranslationKey';
import type {TaskDetailsHistorySearch} from '../sortUtils';
import {ColumnHeader} from './ColumnHeader';
import styles from './HistoryTable.module.scss';

type HeaderConfig = {
	key: string;
	header: string;
	sortKey?: string;
	isDisabled: boolean;
};

const HEADERS_MAP = {
	operation: {
		key: 'operation',
		header: 'tasklist.taskDetailsHistoryOperationHeader',
		sortKey: 'operationType',
		isDisabled: false,
	},
	details: {
		key: 'details',
		header: 'tasklist.taskDetailsHistoryDetailsHeader',
		sortKey: undefined,
		isDisabled: true,
	},
	actor: {
		key: 'actor',
		header: 'tasklist.taskDetailsHistoryActorHeader',
		sortKey: 'actorId',
		isDisabled: false,
	},
	date: {
		key: 'date',
		header: 'tasklist.taskDetailsHistoryDateHeader',
		sortKey: 'timestamp',
		isDisabled: false,
	},
	actions: {
		key: 'actions',
		header: '',
		sortKey: undefined,
		isDisabled: true,
	},
} as const satisfies Record<string, HeaderConfig>;

const HEADERS = [HEADERS_MAP.operation, HEADERS_MAP.details, HEADERS_MAP.actor, HEADERS_MAP.date, HEADERS_MAP.actions];

function isHeaderKey(key: string): key is keyof typeof HEADERS_MAP {
	return key in HEADERS_MAP;
}

type RowData = {
	id: string;
	operation: string;
	details: React.ReactNode;
	actor: string;
	date: string;
	actions: string;
};

type RowCellValues = [RowData['operation'], RowData['details'], RowData['actor'], RowData['date'], RowData['actions']];

type Props = {
	userTaskKey: string;
	auditLogs: AuditLog[];
	search: TaskDetailsHistorySearch;
};

const HistoryTable: React.FC<Props> = ({userTaskKey, auditLogs, search}) => {
	const {t} = useTranslation();

	const headers = useMemo<DataTableHeader[]>(
		() =>
			HEADERS.map((header) => ({
				...header,
				header: header.header === '' ? '' : t(header.header),
			})),
		[t],
	);

	const rows = useMemo<RowData[]>(
		() =>
			auditLogs.map((log) => ({
				id: log.auditLogKey,
				operation: t(getOperationTypeTranslationKey(log.operationType)),
				details:
					log.operationType === 'ASSIGN' ? (
						<>
							<div className={styles.detailsLabel}>{t('tasklist.taskDetailsHistoryPropertyAssignee')}</div>
							{log.relatedEntityKey}
						</>
					) : (
						'-'
					),
				actor: log.actorId,
				date: formatHistoryDate(log.timestamp),
				actions: log.auditLogKey,
			})),
		[auditLogs, t],
	);

	return (
		<DataTable<RowData, RowCellValues> rows={rows} headers={headers} isSortable>
			{({rows, headers, getTableProps, getRowProps}) => (
				<TableContainer className={styles.tableContainer}>
					<Table {...getTableProps()} size="sm" isSortable>
						<TableHead>
							<TableRow>
								{headers.map(({header, key}) => {
									if (!isHeaderKey(key)) {
										return null;
									}

									return (
										<ColumnHeader
											key={key}
											label={HEADERS_MAP[key].header === '' ? '' : t(HEADERS_MAP[key].header)}
											search={search}
											sortKey={HEADERS_MAP[key].sortKey}
											isDisabled={HEADERS_MAP[key].isDisabled}
										>
											{header}
										</ColumnHeader>
									);
								})}
							</TableRow>
						</TableHead>
						<TableBody>
							{rows.map((row) => {
								const {key, ...rowProps} = getRowProps({row});
								const auditLogKey = row.id;

								return (
									<TableRow key={key} {...rowProps}>
										{row.cells.map((cell) => (
											<TableCell key={cell.id}>
												{cell.info.header === 'actions' ? (
													<Link
														className={cn(
															'cds--btn',
															'cds--btn--sm',
															'cds--layout--size-sm',
															'cds--btn--ghost',
															'cds--btn--icon-only',
														)}
														to="/tasklist/$userTaskKey/history/$auditLogKey"
														params={{userTaskKey, auditLogKey}}
														search={search}
														aria-label={t('tasklist.taskDetailsHistoryDetailsLabel')}
														title={t('tasklist.taskDetailsHistoryDetailsLabel')}
													>
														<Information />
													</Link>
												) : (
													cell.value
												)}
											</TableCell>
										))}
									</TableRow>
								);
							})}
						</TableBody>
					</Table>
				</TableContainer>
			)}
		</DataTable>
	);
};

export {HistoryTable};
