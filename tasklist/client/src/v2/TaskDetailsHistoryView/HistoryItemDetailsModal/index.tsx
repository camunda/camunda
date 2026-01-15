/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type LoaderFunctionArgs, redirect, useNavigate} from 'react-router-dom';
import {useSuspenseQuery, type InfiniteData} from '@tanstack/react-query';
import {t} from 'i18next';
import type {QueryUserTaskAuditLogsResponseBody} from '@camunda/camunda-api-zod-schemas/8.9';
import {reactQueryClient} from 'common/react-query/reactQueryClient';
import {pages, useHistoryItemDetailsParams} from 'common/routing';
import {notificationsStore} from 'common/notifications/notifications.store';
import {getUserTaskAuditLogsQueryOptions} from 'v2/api/useUserTaskAuditLogs.query';
import {
  getAuditLogQueryOptions,
  getAuditLogQueryKey,
} from 'v2/api/useAuditLog.query';
import {DetailsModal} from './DetailsModal';

async function loader({params}: LoaderFunctionArgs) {
  const userTaskKey = params.id;
  const auditLogKey = params.auditLogKey;

  if (!userTaskKey || !auditLogKey) {
    throw new Error('User task key and audit log key are required');
  }

  const cachedPages = reactQueryClient.getQueryData<
    InfiniteData<QueryUserTaskAuditLogsResponseBody>
  >(getUserTaskAuditLogsQueryOptions(userTaskKey).queryKey);

  const cachedAuditLog = cachedPages?.pages
    .flatMap((page) => page.items)
    .find((log) => log.auditLogKey === auditLogKey);

  if (cachedAuditLog) {
    reactQueryClient.setQueryData(
      getAuditLogQueryKey(auditLogKey),
      cachedAuditLog,
    );
    return null;
  }

  try {
    await reactQueryClient.ensureQueryData(
      getAuditLogQueryOptions(auditLogKey),
    );
  } catch {
    notificationsStore.displayNotification({
      kind: 'error',
      title: t('taskDetailsHistoryAuditLogErrorTitle'),
      isDismissable: true,
    });

    return redirect(pages.taskDetailsHistory(userTaskKey));
  }

  return null;
}

const HistoryItemDetailsModal: React.FC = () => {
  const {id, auditLogKey} = useHistoryItemDetailsParams();
  const navigate = useNavigate();
  const {data: auditLog} = useSuspenseQuery(
    getAuditLogQueryOptions(auditLogKey),
  );

  const handleClose = () => {
    navigate(pages.taskDetailsHistory(id));
  };

  return <DetailsModal auditLog={auditLog} onClose={handleClose} />;
};

// eslint-disable-next-line react-refresh/only-export-components
export {HistoryItemDetailsModal as Component, loader};
