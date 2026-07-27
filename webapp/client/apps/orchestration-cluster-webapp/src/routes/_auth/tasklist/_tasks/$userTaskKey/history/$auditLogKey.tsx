/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';
import {createFileRoute, redirect, stripSearchParams, useNavigate} from '@tanstack/react-router';
import {useSuspenseQuery, type InfiniteData} from '@tanstack/react-query';
import type {QueryUserTaskAuditLogsResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {queries} from '#/shared/http/queries';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {getAuditLogsRequestBody} from '#/tasklist/modules/task-details-history/getAuditLogsRequestBody';
import {
	getAuditLogSort,
	taskDetailsHistorySearchDefaults,
	taskDetailsHistorySearchSchema,
} from '#/tasklist/modules/task-details-history/sortUtils';
import {HistoryItemDetailsModal} from '#/tasklist/modules/task-details-history/components/HistoryItemDetailsModal';
import {useCallback} from 'react';

export const Route = createFileRoute('/_auth/tasklist/_tasks/$userTaskKey/history/$auditLogKey')({
	validateSearch: taskDetailsHistorySearchSchema,
	search: {
		middlewares: [stripSearchParams(taskDetailsHistorySearchDefaults)],
	},
	loader: async ({context: {queryClient}, params: {userTaskKey, auditLogKey}, location}) => {
		const search = taskDetailsHistorySearchSchema.parse(location.search);
		const auditLogsQuery = queries.queryUserTaskAuditLogs(
			userTaskKey,
			getAuditLogsRequestBody(getAuditLogSort(search)),
		);
		const cachedPages = queryClient.getQueryData<InfiniteData<QueryUserTaskAuditLogsResponseBody>>(
			auditLogsQuery.queryKey,
		);
		const cachedAuditLog = cachedPages?.pages
			.flatMap((page) => page.items)
			.find((log) => log.auditLogKey === auditLogKey);

		if (cachedAuditLog !== undefined) {
			queryClient.setQueryData(queries.getAuditLog(auditLogKey).queryKey, cachedAuditLog);
			return;
		}

		try {
			await queryClient.ensureQueryData(queries.getAuditLog(auditLogKey));
		} catch {
			notificationsStore.displayNotification({
				kind: 'error',
				title: t('tasklist.taskDetailsHistoryAuditLogErrorTitle'),
				isDismissable: true,
			});

			throw redirect({
				to: '/tasklist/$userTaskKey/history',
				params: {userTaskKey},
				search,
			});
		}
	},
	component: function HistoryItemDetailsModalRoute() {
		const {userTaskKey, auditLogKey} = Route.useParams();
		const search = Route.useSearch();
		const navigate = useNavigate();
		const {data: auditLog} = useSuspenseQuery(queries.getAuditLog(auditLogKey));

		const handleClose = useCallback(() => {
			void navigate({to: '/tasklist/$userTaskKey/history', params: {userTaskKey}, search});
		}, [navigate, userTaskKey, search]);

		return <HistoryItemDetailsModal auditLog={auditLog} onClose={handleClose} />;
	},
});
