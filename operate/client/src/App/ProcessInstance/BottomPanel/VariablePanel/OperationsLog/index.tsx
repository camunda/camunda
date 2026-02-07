/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useState} from 'react';
import {observer} from 'mobx-react';
import {formatDate} from 'modules/utils/date';
import {useAuditLogs} from 'modules/queries/auditLog/useAuditLogs';
import {
  type AuditLog,
  type QueryAuditLogsRequestBody,
  auditLogSortFieldEnum,
} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {Container, OperationLogName} from './styled';
import {PaginatedSortableTable} from 'modules/components/PaginatedSortableTable';
import {getSortParams} from 'modules/utils/filter';
import {useLocation} from 'react-router-dom';
import {Information} from '@carbon/react/icons';
import {Button} from '@carbon/react';
import {notificationsStore} from 'modules/stores/notifications';
import {logger} from 'modules/logger';
import {tracking} from 'modules/tracking';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {
  DetailsModal,
  type DetailsModalState,
} from 'modules/components/OperationsLogDetailsModal';
import {OperationsLogStateIcon} from 'modules/components/OperationsLogStateIcon';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';

type Props = {
  isVisible: boolean;
};

const headerColumns = [
  {
    header: 'Operation',
    key: 'operationType',
    sortKey: 'operationType',
  },
  {
    header: 'Status',
    key: 'result',
  },
  {
    header: 'Actor',
    key: 'user',
    sortKey: 'actorId',
  },
  {
    header: 'Time',
    key: 'timestamp',
    sortKey: 'timestamp',
  },
  {
    header: '',
    key: 'comment',
    isDisabled: true,
  },
];

const OperationsLog: React.FC<Props> = observer(({isVisible}) => {
  const location = useLocation();
  const sortParams = getSortParams(location.search) || {
    sortBy: 'timestamp',
    sortOrder: 'desc',
  };
  const sortByParsed = auditLogSortFieldEnum.safeParse(sortParams.sortBy);
  const sortBy = sortByParsed.success ? sortByParsed.data : 'timestamp';
  const {data: processInstance} = useProcessInstance();
  const {resolvedElementInstance, isFetchingElement} =
    useProcessInstanceElementSelection();
  const elementInstanceKey = resolvedElementInstance?.elementInstanceKey;

  const request = useMemo(
    (): QueryAuditLogsRequestBody => ({
      sort: [
        {
          field: sortBy,
          order: sortParams.sortOrder,
        },
      ],
      filter: {
        category: {$neq: 'ADMIN'},
        processInstanceKey: processInstance?.processInstanceKey,
        elementInstanceKey: elementInstanceKey ?? undefined,
      },
    }),
    [
      sortBy,
      sortParams.sortOrder,
      processInstance?.processInstanceKey,
      elementInstanceKey,
    ],
  );

  const {
    data,
    isLoading,
    error,
    isFetchingPreviousPage,
    hasPreviousPage,
    fetchPreviousPage,
    isFetchingNextPage,
    hasNextPage,
    fetchNextPage,
  } = useAuditLogs(request, {
    enabled: isVisible && !isFetchingElement,
    select: (data) => {
      tracking.track({
        eventName: 'audit-logs-loaded',
        filters: Object.keys(request.filter ?? {}),
        sort: request.sort,
      });
      return {
        auditLogs: data.pages.flatMap((page) => page.items),
        totalCount: data.pages.at(0)?.page.totalItems ?? 0,
      };
    },
  });

  const [detailsModal, setDetailsModal] = useState<DetailsModalState>({
    isOpen: false,
  });

  useEffect(() => {
    if (error !== null) {
      tracking.track({
        eventName: 'audit-logs-fetch-failed',
      });
      notificationsStore.displayNotification({
        isDismissable: true,
        kind: 'error',
        title: 'Audit logs could not be fetched',
      });
      logger.error(error);
    }
  }, [error]);

  const rows = useMemo(
    () =>
      data?.auditLogs.map((item: AuditLog) => ({
        id: item.auditLogKey,
        operationType: `${spaceAndCapitalize(item.operationType.toString())} ${spaceAndCapitalize(
          item.entityType.toString(),
        )}`,
        result: (
          <OperationLogName>
            <OperationsLogStateIcon
              state={item.result}
              data-testid={`${item.auditLogKey}-icon`}
            />
            {spaceAndCapitalize(item.result.toString())}
          </OperationLogName>
        ),
        user: item.actorId,
        timestamp: formatDate(item.timestamp),
        comment: (
          <Button
            kind="ghost"
            size="sm"
            tooltipPosition="left"
            iconDescription="Open details"
            aria-label="Open details"
            onClick={() => setDetailsModal({isOpen: true, auditLog: item})}
            hasIconOnly
            renderIcon={Information}
          />
        ),
      })) || [],
    [data],
  );

  const getTableState = () => {
    if (!isVisible) {
      return 'skeleton';
    } else if (isLoading) {
      return 'loading';
    } else if (error) {
      return 'error';
    } else if (rows.length === 0) {
      return 'empty';
    }
    return 'content';
  };

  return (
    <Container>
      <PaginatedSortableTable
        state={getTableState()}
        rows={rows}
        emptyMessage={{
          message: 'No operations found for this instance',
        }}
        headerColumns={headerColumns}
        pagination={{
          hasPreviousPage,
          hasNextPage,
          isFetchingPreviousPage,
          isFetchingNextPage,
          fetchPreviousPage,
          fetchNextPage,
        }}
      />
      {detailsModal.auditLog && (
        <DetailsModal
          isOpen={detailsModal.isOpen}
          onClose={() => setDetailsModal({isOpen: false})}
          auditLog={detailsModal.auditLog}
        />
      )}
    </Container>
  );
});

export {OperationsLog};
