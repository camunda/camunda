/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useState} from 'react';
import {useLocation, useSearchParams} from 'react-router-dom';
import {useAuditLogs} from 'modules/queries/auditLog/useAuditLogs';
import {formatDate} from 'modules/utils/date';
import {parseSortParamsV2} from 'modules/utils/filter';
import {
  type AuditLog,
  type QueryAuditLogsRequestBody,
  queryAuditLogsRequestBodySchema,
} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {tracking} from 'modules/tracking';
import {notificationsStore} from 'modules/stores/notifications';
import {logger} from 'modules/logger';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';
import {Container} from './styled';
import {PanelHeader as BasePanelHeader} from 'modules/components/PanelHeader';
import {
  DetailsModal,
  type DetailsModalState,
} from 'modules/components/OperationsLogDetailsModal';
import {
  AUDIT_LOG_FILTER_FIELDS,
  type OperationsLogFilterField,
  type OperationsLogFilters,
} from '../auditLogFilters';
import {getFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {observer} from 'mobx-react';
import {
  useProcessDefinitionNames,
  useSelectedProcessDefinition,
} from 'modules/hooks/processDefinitions';
import {
  auditLogEntityTypeSchema,
  auditLogOperationTypeSchema,
  auditLogResultSchema,
} from '@camunda/camunda-api-zod-schemas/8.9';
import {formatToISO} from 'modules/utils/date/formatDate';
import {PaginatedSortableTable} from 'modules/components/PaginatedSortableTable';
import {CellOperationType} from './Cell/CellOperationType';
import {CellResult} from './Cell/CellResult';
import {CellReference} from './Cell/CellReference';
import {CellProperty} from './Cell/CellProperty';
import {CellActor} from './Cell/CellActor';
import {CellComment} from './Cell/CellComment';

const headerColumns = [
  {
    header: '',
    key: 'result',
  },
  {
    header: 'Operation type',
    key: 'operationType',
    sortKey: 'operationType',
  },
  {
    header: 'Entity type',
    key: 'entityType',
    sortKey: 'entityType',
  },
  {
    header: 'Reference to entity',
    key: 'reference',
    isDisabled: true,
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

const AuditLogsSearchSortFieldSchema =
  queryAuditLogsRequestBodySchema.shape.sort.unwrap().unwrap().shape.field;

const InstancesTable: React.FC = observer(() => {
  const [params] = useSearchParams();
  const sort = parseSortParamsV2(params, AuditLogsSearchSortFieldSchema, {
    field: 'timestamp',
    order: 'desc',
  });
  const location = useLocation();
  const filterValues = getFilters<
    OperationsLogFilterField,
    OperationsLogFilters
  >(location.search, AUDIT_LOG_FILTER_FIELDS, []);
  const selectedTenantId =
    filterValues.tenant === 'all' ? undefined : filterValues.tenant;
  const {data: selectedProcessDefinition} = useSelectedProcessDefinition();

  const request: QueryAuditLogsRequestBody = useMemo(() => {
    return {
      sort: [
        {
          field: sort[0].field,
          order: sort[0].order,
        },
      ],
      filter: {
        category: {$neq: 'ADMIN'},
        processDefinitionKey: selectedProcessDefinition?.processDefinitionKey,
        processInstanceKey: filterValues.processInstanceKey,
        tenantId: selectedTenantId,
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
        result: filterValues.result
          ? auditLogResultSchema.parse(filterValues.result)
          : undefined,
        timestamp:
          filterValues.timestampBefore || filterValues.timestampAfter
            ? {
                $gt: formatToISO(filterValues.timestampAfter),
                $lt: formatToISO(filterValues.timestampBefore),
              }
            : undefined,
        actorId: filterValues.actorId,
      },
    };
  }, [
    filterValues,
    selectedTenantId,
    selectedProcessDefinition?.processDefinitionKey,
    sort,
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

  const {
    data: processDefinitionNameMap,
    isLoading: isLoadingProcessDefinitionNames,
  } = useProcessDefinitionNames(selectedTenantId);

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
        operationType: <CellOperationType item={item} />,
        entityType: spaceAndCapitalize(item.entityType),
        reference: (
          <CellReference
            item={item}
            processDefinitionName={
              item.processDefinitionKey
                ? processDefinitionNameMap?.[item.processDefinitionKey]
                : undefined
            }
          />
        ),
        property: <CellProperty item={item} />,
        user: <CellActor item={item} />,
        timestamp: formatDate(item.timestamp),
        comment: <CellComment item={item} setDetailsModal={setDetailsModal} />,
      })) || [],
    [data, processDefinitionNameMap],
  );

  const getTableState = () => {
    if (isLoading || isLoadingProcessDefinitionNames) {
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
      <PaginatedSortableTable
        state={getTableState()}
        rows={rows}
        emptyMessage={{
          message: 'No operations log found',
          additionalInfo: 'Try adjusting your filters or check back later.',
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
        stickyHeader
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
