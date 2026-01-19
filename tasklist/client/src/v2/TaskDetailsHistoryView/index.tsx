/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {
  type LoaderFunctionArgs,
  Outlet,
  useNavigate,
  useRevalidator,
  useRouteError,
} from 'react-router-dom';
import {pages, useTaskDetailsParams} from 'common/routing';
import {reactQueryClient} from 'common/react-query/reactQueryClient';
import {getUserTaskAuditLogsQueryOptions} from 'v2/api/useUserTaskAuditLogs.query';
import {useSuspenseInfiniteQuery} from '@tanstack/react-query';
import {SomethingWentWrong} from 'common/error-handling/SomethingWentWrong';
import {Forbidden} from 'common/error-handling/Forbidden';
import {useTranslation} from 'react-i18next';
import {logger} from 'common/utils/logger';
import {requestErrorSchema} from 'common/api/request';
import {
  DataTable,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Layer,
  Button,
} from '@carbon/react';
import {Information} from '@carbon/react/icons';
import {formatDate} from 'common/dates/formatDate';
import {spaceAndCapitalize} from 'common/utils/spaceAndCapitalize';
import {AuditLogItemStatusIcon} from 'v2/features/tasks/task-history/AuditLogItemStatusIcon';
import styles from './styles.module.scss';

const HTTP_STATUS_FORBIDDEN = 403;

const HEADERS = [
  {key: 'operation', header: 'taskDetailsHistoryOperationHeader'},
  {key: 'status', header: 'taskDetailsHistoryStatusHeader'},
  {key: 'actor', header: 'taskDetailsHistoryActorHeader'},
  {key: 'time', header: 'taskDetailsHistoryTimeHeader'},
  {key: 'details', header: ''},
];

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
  const {t} = useTranslation();
  const navigate = useNavigate();
  const {data} = useSuspenseInfiniteQuery(getUserTaskAuditLogsQueryOptions(id));

  const auditLogs = useMemo(
    () => data.pages.flatMap((page) => page.items),
    [data],
  );

  const headers = HEADERS.map((header) => ({
    ...header,
    header: header.header ? t(header.header) : '',
  }));

  const rows = useMemo(
    () =>
      auditLogs.map((log) => ({
        id: log.auditLogKey,
        operation: `${spaceAndCapitalize(log.operationType)} ${spaceAndCapitalize(log.entityType)}`,
        status: log.result,
        actor: log.actorId,
        time: formatDate(log.timestamp),
        details: log.auditLogKey,
      })),
    [auditLogs],
  );

  const handleOpenDetails = (auditLogKey: string) => {
    navigate(pages.taskDetailsHistoryAuditLog(id, auditLogKey));
  };

  if (rows.length === 0) {
    return (
      <div
        className={styles.emptyContainer}
        data-testid="task-details-history-view"
      >
        <Layer>
          <p>{t('taskDetailsHistoryEmptyMessage')}</p>
        </Layer>
      </div>
    );
  }

  return (
    <div className={styles.container} data-testid="task-details-history-view">
      <div className={styles.tableContainer}>
        <DataTable rows={rows} headers={headers}>
          {({rows, headers, getTableProps, getHeaderProps, getRowProps}) => (
            <TableContainer>
              <Table {...getTableProps()} size="sm">
                <TableHead>
                  <TableRow>
                    {headers.map((header) => {
                      const {key, ...headerProps} = getHeaderProps({header});
                      return (
                        <TableHeader key={key} {...headerProps}>
                          {header.header}
                        </TableHeader>
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
                            {cell.info.header === 'status' ? (
                              <div className={styles.statusCell}>
                                <AuditLogItemStatusIcon status={cell.value} />
                                {spaceAndCapitalize(cell.value)}
                              </div>
                            ) : (
                              <>
                                {cell.info.header === 'details' ? (
                                  <Button
                                    kind="ghost"
                                    size="sm"
                                    tooltipPosition="left"
                                    iconDescription={t(
                                      'taskDetailsHistoryDetailsLabel',
                                    )}
                                    aria-label={t(
                                      'taskDetailsHistoryDetailsLabel',
                                    )}
                                    onClick={() =>
                                      handleOpenDetails(cell.value)
                                    }
                                    hasIconOnly
                                    renderIcon={Information}
                                  />
                                ) : (
                                  cell.value
                                )}
                              </>
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
      </div>
      <Outlet />
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
