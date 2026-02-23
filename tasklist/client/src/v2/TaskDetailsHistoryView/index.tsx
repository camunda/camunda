/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useRef, useCallback} from 'react';
import {
  type LoaderFunctionArgs,
  Outlet,
  useLocation,
  useNavigate,
  useRevalidator,
  useRouteError,
} from 'react-router-dom';
import {pages, useTaskDetailsParams} from 'common/routing';
import {reactQueryClient} from 'common/react-query/reactQueryClient';
import {
  getUserTaskAuditLogsQueryOptions,
  DEFAULT_SORT,
  AUDIT_LOG_SORT_FIELDS,
  type AuditLogSort,
  type AuditLogSortField,
} from 'v2/api/useUserTaskAuditLogs.query';
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
  TableRow,
  Layer,
  Button,
} from '@carbon/react';
import {Information} from '@carbon/react/icons';
import {formatDate} from 'common/dates/formatDate';
import {ColumnHeader} from './ColumnHeader';
import {getOperationTypeTranslationKey} from './getOperationTypeTranslationKey';
import {getSortParams} from './sortUtils';
import styles from './styles.module.scss';

const HTTP_STATUS_FORBIDDEN = 403;

type HeaderConfig = {
  key: string;
  header: string;
  sortKey?: AuditLogSortField;
  isDefault?: boolean;
  isDisabled?: boolean;
};

const HEADERS_MAP = {
  operation: {
    key: 'operation',
    header: 'taskDetailsHistoryOperationHeader',
    sortKey: 'operationType',
    isDefault: false,
    isDisabled: false,
  },
  property: {
    key: 'property',
    header: 'taskDetailsHistoryPropertyHeader',
    sortKey: undefined,
    isDefault: false,
    isDisabled: true,
  },
  actor: {
    key: 'actor',
    header: 'taskDetailsHistoryActorHeader',
    sortKey: 'actorId',
    isDefault: false,
    isDisabled: false,
  },
  time: {
    key: 'time',
    header: 'taskDetailsHistoryTimeHeader',
    sortKey: 'timestamp',
    isDefault: true,
    isDisabled: false,
  },
  details: {
    key: 'details',
    header: '',
    sortKey: undefined,
    isDefault: false,
    isDisabled: true,
  },
} as const;

function isHeaderKey(key: string): key is keyof typeof HEADERS_MAP {
  return key in HEADERS_MAP;
}

const HEADERS: HeaderConfig[] = [
  HEADERS_MAP['operation'],
  HEADERS_MAP['property'],
  HEADERS_MAP['actor'],
  HEADERS_MAP['time'],
  HEADERS_MAP['details'],
];

function getSortFromUrl(search: string): AuditLogSort {
  const sortParams = getSortParams(search);
  if (sortParams === null) {
    return DEFAULT_SORT;
  }

  if (AUDIT_LOG_SORT_FIELDS.includes(sortParams.sortBy)) {
    return {
      field: sortParams.sortBy,
      order: sortParams.sortOrder,
    };
  }

  return DEFAULT_SORT;
}

async function loader({params, request}: LoaderFunctionArgs) {
  const userTaskKey = params.id;
  if (!userTaskKey) {
    throw new Error('User task key is required');
  }

  await reactQueryClient.ensureInfiniteQueryData(
    getUserTaskAuditLogsQueryOptions(
      userTaskKey,
      getSortFromUrl(new URL(request.url).search),
    ),
  );

  return null;
}

const TaskDetailsHistoryView: React.FC = () => {
  const {id} = useTaskDetailsParams();
  const {t} = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const scrollableContainerRef = useRef<HTMLDivElement>(null);

  const {data, fetchNextPage, hasNextPage, isFetchingNextPage} =
    useSuspenseInfiniteQuery(
      getUserTaskAuditLogsQueryOptions(id, getSortFromUrl(location.search)),
    );

  const auditLogs = useMemo(
    () => data.pages.flatMap((page) => page.items),
    [data],
  );

  const handleScroll: React.UIEventHandler<HTMLDivElement> = useCallback(
    async (event) => {
      const target = event.currentTarget;
      const {scrollTop, scrollHeight, clientHeight} = target;

      const isAtBottom =
        Math.floor(scrollHeight - clientHeight - scrollTop) <= 1;

      if (isAtBottom && hasNextPage && !isFetchingNextPage) {
        await fetchNextPage();
      }
    },
    [hasNextPage, isFetchingNextPage, fetchNextPage],
  );

  const headers = HEADERS.map((header) => ({
    ...header,
    header: header.header ? t(header.header) : '',
  }));

  const rows = useMemo(
    () =>
      auditLogs.map((log) => ({
        id: log.auditLogKey,
        operation: t(getOperationTypeTranslationKey(log.operationType)),
        property:
          log.operationType === 'ASSIGN' ? (
            <>
              <div className={styles.propertyLabel}>
                {t('taskDetailsHistoryPropertyAssignee')}
              </div>
              {log.relatedEntityKey}
            </>
          ) : (
            '-'
          ),
        actor: log.actorId,
        time: formatDate(log.timestamp),
        details: log.auditLogKey,
      })),
    [auditLogs, t],
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
      <div
        className={styles.tableContainer}
        ref={scrollableContainerRef}
        onScroll={handleScroll}
      >
        <DataTable rows={rows} headers={headers} isSortable>
          {({rows, headers, getTableProps, getRowProps}) => (
            <TableContainer>
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
                          label={t(HEADERS_MAP[key].header)}
                          sortKey={HEADERS_MAP[key].sortKey}
                          isDefault={HEADERS_MAP[key].isDefault}
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
                            {cell.info.header === 'details' ? (
                              <Button
                                kind="ghost"
                                size="sm"
                                tooltipPosition="left"
                                iconDescription={t(
                                  'taskDetailsHistoryDetailsLabel',
                                )}
                                aria-label={t('taskDetailsHistoryDetailsLabel')}
                                onClick={() => handleOpenDetails(cell.value)}
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
