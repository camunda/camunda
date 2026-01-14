/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  type LoaderFunctionArgs,
  useRevalidator,
  useRouteError,
} from 'react-router-dom';
import {useTaskDetailsParams} from 'common/routing';
import {reactQueryClient} from 'common/react-query/reactQueryClient';
import {getUserTaskAuditLogsQueryOptions} from 'v2/api/useUserTaskAuditLogs.query';
import {useSuspenseInfiniteQuery} from '@tanstack/react-query';
import {SomethingWentWrong} from 'common/error-handling/SomethingWentWrong';
import {Forbidden} from 'common/error-handling/Forbidden';
import {useTranslation} from 'react-i18next';
import {logger} from 'common/utils/logger';
import {requestErrorSchema} from 'common/api/request';

const HTTP_STATUS_FORBIDDEN = 403;

async function loader({params}: LoaderFunctionArgs) {
  const userTaskKey = params.id;
  if (!userTaskKey) {
    throw new Error('User task key is required');
  }

  await reactQueryClient.ensureInfiniteQueryData(
    getUserTaskAuditLogsQueryOptions(userTaskKey),
  );

  return null;
}

const TaskDetailsHistoryView: React.FC = () => {
  const {id} = useTaskDetailsParams();
  const {data} = useSuspenseInfiniteQuery(getUserTaskAuditLogsQueryOptions(id));

  return (
    <div data-testid="task-details-history-view">
      <code>{JSON.stringify(data, null, 2)}</code>
    </div>
  );
};

const ErrorBoundary: React.FC = () => {
  const error = useRouteError();
  const {t} = useTranslation();
  const revalidator = useRevalidator();

  logger.error(error);

  const {data: parsedError, success} = requestErrorSchema.safeParse(error);
  if (success && parsedError.variant === 'failed-response') {
    if (parsedError.response.status === HTTP_STATUS_FORBIDDEN) {
      return (
        <Forbidden
          titleKey="taskDetailsHistoryForbiddenTitle"
          descKey="taskDetailsHistoryForbiddenDesc"
          linkLabelKey="taskDetailsHistoryForbiddenLinkLabel"
        />
      );
    }
  }

  return (
    <SomethingWentWrong
      title={t('taskDetailsHistoryErrorTitle')}
      message={t('taskDetailsHistoryErrorMessage')}
      onRetryClick={revalidator.revalidate}
    />
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export {TaskDetailsHistoryView as Component, loader, ErrorBoundary};
