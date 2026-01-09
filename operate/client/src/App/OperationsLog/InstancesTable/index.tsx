/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useState} from 'react';
import {useLocation} from 'react-router-dom';
import {useAuditLogs} from 'modules/queries/auditLog/useAuditLogs';
import {SortableTable} from 'modules/components/SortableTable';
import {formatDate} from 'modules/utils/date';
import {getSortParams} from 'modules/utils/filter';
import {
  type AuditLog,
  auditLogSortFieldEnum,
  type QueryAuditLogsRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {tracking} from 'modules/tracking';
import {notificationsStore} from 'modules/stores/notifications';
import {logger} from 'modules/logger';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {Container} from './styled';
import {PanelHeader as BasePanelHeader} from 'modules/components/PanelHeader';
import {processesStore} from 'modules/stores/processes/processes.list';
import {
  DetailsModal,
  type DetailsModalState,
} from 'modules/components/OperationsLogDetailsModal';
import {
  AUDIT_LOG_FILTER_FIELDS,
  type OperationsLogFilterField,
  type OperationsLogFilters,
} from '../shared';
import {getFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {observer} from 'mobx-react';
import {CellAppliedTo} from './CellAppliedTo';
import {CellOperationType} from './CellOperationType';
import {CellResult} from './CellResult';
import {CellComment} from './CellComment.tsx';

const ROW_HEIGHT = 46;
const SMOOTH_SCROLL_STEP_SIZE = 5 * ROW_HEIGHT;

const headerColumns = [
  {
    header: 'Operation',
    key: 'operationType',
    sortKey: 'operationType',
  },
  {
    header: 'Entity',
    key: 'entityType',
    sortKey: 'entityType',
  },
  {
    header: 'Status',
    key: 'result',
  },
  {
    header: 'Applied to',
    key: 'appliedTo',
    isDisabled: true,
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

const InstancesTable: React.FC = observer(() => {
  const location = useLocation();
  const sortParams = getSortParams(location.search) || {
    sortBy: 'timestamp',
    sortOrder: 'desc',
  };
  const sortByParsed = auditLogSortFieldEnum.safeParse(sortParams.sortBy);
  const sortBy = sortByParsed.success ? sortByParsed.data : 'timestamp';
  const filterValues = getFilters<
    OperationsLogFilterField,
    OperationsLogFilters
  >(location.search, AUDIT_LOG_FILTER_FIELDS, []);
  const processId = processesStore.getProcessIdByLocation(location);

  const request: QueryAuditLogsRequestBody = useMemo(() => {
    return {
      sort: [
        {
          field: sortBy,
          order: sortParams.sortOrder,
        },
      ],
      filter: {
        category: {$neq: 'ADMIN'},
        processDefinitionKey: processId,
        processInstanceKey: filterValues.processInstanceKey,
        tenantId: filterValues.tenant,
      },
    };
  }, [
    filterValues.processInstanceKey,
    filterValues.tenant,
    processId,
    sortBy,
    sortParams.sortOrder,
  ]);

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
    enabled: true,
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
        entityType: spaceAndCapitalize(item.entityType),
        operationType: <CellOperationType item={item} />,
        result: <CellResult item={item} />,
        appliedTo: <CellAppliedTo item={item} />,
        user: item.actorId,
        timestamp: formatDate(item.timestamp),
        comment: <CellComment item={item} setDetailsModal={setDetailsModal} />,
      })) || [],
    [data],
  );

  const getTableState = () => {
    if (isLoading) {
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
      <BasePanelHeader title="Operations Log" count={data?.totalCount} />
      <SortableTable
        state={getTableState()}
        rows={rows}
        emptyMessage={{
          message: 'No operations log found',
          additionalInfo: 'Try adjusting your filters or check back later.',
        }}
        onVerticalScrollStartReach={async (scrollDown) => {
          if (hasPreviousPage && !isFetchingPreviousPage) {
            await fetchPreviousPage();
            scrollDown(SMOOTH_SCROLL_STEP_SIZE);
          }
        }}
        onVerticalScrollEndReach={() => {
          if (hasNextPage && !isFetchingNextPage) {
            fetchNextPage();
          }
        }}
        headerColumns={headerColumns}
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

export {InstancesTable};
