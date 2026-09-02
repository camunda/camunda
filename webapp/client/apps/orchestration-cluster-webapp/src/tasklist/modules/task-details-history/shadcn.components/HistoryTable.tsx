/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {Button, EmptyState, Table, TableBody, TableCell, TableHeader, TableRow} from '@camunda/design-system';
import {Info} from 'lucide-react';
import {Link} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10';
import {formatHistoryDate} from '../formatHistoryDate';
import {getOperationTypeTranslationKey} from '../getOperationTypeTranslationKey';
import type {TaskDetailsHistorySearch} from '../sortUtils';
import {ColumnHeader} from './ColumnHeader';

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

type RowData = {
	id: string;
	operation: string;
	details: React.ReactNode;
	actor: string;
	date: string;
	actions: string;
};

type Props = {
	userTaskKey: string;
	auditLogs: AuditLog[];
	search: TaskDetailsHistorySearch;
};

const HistoryTable: React.FC<Props> = ({userTaskKey, auditLogs, search}) => {
	const {t} = useTranslation();

	const headers = useMemo(
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
							<div className="text-xs leading-4">{t('tasklist.taskDetailsHistoryPropertyAssignee')}</div>
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

	if (rows.length === 0) {
		return (
			<div className="flex h-32 w-full items-center justify-center rounded-xl border border-border bg-neutral-background-subtle shadow-sm">
				<EmptyState size="sm" heading={t('tasklist.taskDetailsHistoryEmptyMessage')} />
			</div>
		);
	}

	return (
		<Table size="md" aria-label={t('tasklist.taskDetailsHistoryTabLabel')}>
			<TableHeader>
				<TableRow>
					{headers.map(({header, key}) => (
						<ColumnHeader
							key={key}
							label={key === 'actions' ? t('tasklist.taskDetailsHistoryDetailsLabel') : t(HEADERS_MAP[key].header)}
							search={search}
							sortKey={HEADERS_MAP[key].sortKey}
							isDisabled={HEADERS_MAP[key].isDisabled}
						>
							{header}
						</ColumnHeader>
					))}
				</TableRow>
			</TableHeader>
			<TableBody>
				{rows.map((row) => (
					<TableRow key={row.id}>
						<TableCell>{row.operation}</TableCell>
						<TableCell>{row.details}</TableCell>
						<TableCell>{row.actor}</TableCell>
						<TableCell>
							<span className="whitespace-nowrap">{row.date}</span>
						</TableCell>
						<TableCell>
							<Button asChild variant="ghost" size="icon-sm">
								<Link
									to="/shadcn/tasklist/$userTaskKey/history/$auditLogKey"
									params={{userTaskKey, auditLogKey: row.actions}}
									search={search}
									aria-label={t('tasklist.taskDetailsHistoryDetailsLabel')}
									title={t('tasklist.taskDetailsHistoryDetailsLabel')}
								>
									<Info aria-hidden />
								</Link>
							</Button>
						</TableCell>
					</TableRow>
				))}
			</TableBody>
		</Table>
	);
};

export {HistoryTable};
