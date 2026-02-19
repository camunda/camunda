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
import {Container} from './styled';
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
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {EmptyMessageContainer} from '../styled';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {CellResult} from 'App/OperationsLog/InstancesTable/Cell/CellResult';
import {CellProperty} from 'App/OperationsLog/InstancesTable/Cell/CellProperty';
import {CellActor} from 'App/OperationsLog/InstancesTable/Cell/CellActor';
import {Filters} from './Filters';
import {
  auditLogEntityTypeSchema,
  auditLogOperationTypeSchema,
} from '@camunda/camunda-api-zod-schemas/8.9';
import {getFilters} from 'modules/utils/filter/getProcessInstanceFilters.ts';
import {
  PROCESS_INSTANCE_AUDIT_LOG_FILTER_FIELDS,
  type ProcessInstanceOperationsLogFilterField,
  type ProcessInstanceOperationsLogFilters,
} from './operationsLogFilters.ts';

type Props = {
  isVisible: boolean;
};

const headerColumns = [
  {
    header: '',
    key: 'result',
  },
  {
    header: 'Operation Type',
    key: 'operationType',
    sortKey: 'operationType',
  },
  {
    header: 'Entity Type',
    key: 'entityType',
    sortKey: 'entityType',
  },
  {
    header: 'Property',
    key: 'property',
    isDisabled: true,
  },
  {
    header: 'Actor',
    key: 'user',
    sortKey: 'actorId',
  },
  {
    header: 'Date',
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
  const {processInstanceId: processInstanceKey} =
    useProcessInstancePageParams();
  const {resolvedElementInstance, isFetchingElement, hasSelection} =
    useProcessInstanceElementSelection();
  const elementInstanceKey = resolvedElementInstance?.elementInstanceKey;
  const hasMultipleInstances =
    resolvedElementInstance?.elementInstanceKey === undefined && hasSelection;

  const filterValues = getFilters<
    ProcessInstanceOperationsLogFilterField,
    ProcessInstanceOperationsLogFilters
  >(location.search, PROCESS_INSTANCE_AUDIT_LOG_FILTER_FIELDS, []);

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
        processInstanceKey,
        elementInstanceKey: elementInstanceKey ?? undefined,
        operationType: filterValues.operationType
          ? {
              $in: filterValues.operationType
                .split(',')
                .map((v) => auditLogOperationTypeSchema.parse(v)),
            }
          : undefined,
        entityType: filterValues.entityType
          ? {
              $in: filterValues.entityType
                .split(',')
                .map((v) => auditLogEntityTypeSchema.parse(v)),
            }
          : undefined,
      },
    }),
    [
      sortBy,
      sortParams.sortOrder,
      processInstanceKey,
      elementInstanceKey,
      filterValues.operationType,
      filterValues.entityType,
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
    enabled: isVisible && !isFetchingElement && !hasMultipleInstances,
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
        result: <CellResult item={item} />,
        operationType: spaceAndCapitalize(item.operationType.toString()),
        entityType: spaceAndCapitalize(item.entityType.toString()),
        property: <CellProperty item={item} />,
        user: <CellActor item={item} />,
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
    }

    if (isLoading) {
      return 'loading';
    }

    if (error) {
      return 'error';
    }

    if (rows.length === 0) {
      return 'empty';
    }
    return 'content';
  };

  if (hasMultipleInstances) {
    return (
      <Container>
        <EmptyMessageContainer>
          <EmptyMessage message="To view the Operations Log, select a single Element Instance in the Instance History." />
        </EmptyMessageContainer>
      </Container>
    );
  }

  return (
    <Container>
      <Filters />
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
