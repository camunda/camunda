/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo} from 'react';
import {Button, DataTable, Table, TableBody, TableCell, TableContainer, TableHead, TableRow} from '@carbon/react';
import {Information} from '@carbon/react/icons';
import {useNavigate, useParams} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10';
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

type Props = {
	auditLogs: AuditLog[];
	search: TaskDetailsHistorySearch;
};

const HistoryTable: React.FC<Props> = ({auditLogs, search}) => {
	const {t} = useTranslation();
	const navigate = useNavigate();
	const {userTaskKey} = useParams({from: '/_auth/tasklist/_tasks/$userTaskKey/history'});

	const headers = useMemo(
		() =>
			HEADERS.map((header) => ({
				...header,
				header: header.header === '' ? '' : t(header.header),
			})),
		[t],
	);

	const handleOpenDetails = useCallback(
		(auditLogKey: string) => {
			void navigate({
				to: '/tasklist/$userTaskKey/history/$auditLogKey',
				params: {userTaskKey, auditLogKey},
				search,
			});
		},
		[navigate, userTaskKey, search],
	);

	const rows = useMemo(
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
		<DataTable rows={rows} headers={headers} isSortable>
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
								return (
									<TableRow key={key} {...rowProps}>
										{row.cells.map((cell) => (
											<TableCell key={cell.id}>
												{cell.info.header === 'actions' ? (
													<Button
														kind="ghost"
														size="sm"
														tooltipPosition="left"
														iconDescription={t('tasklist.taskDetailsHistoryDetailsLabel')}
														aria-label={t('tasklist.taskDetailsHistoryDetailsLabel')}
														onClick={() => {
															handleOpenDetails(cell.value as string);
														}}
														hasIconOnly
														renderIcon={Information}
													/>
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
