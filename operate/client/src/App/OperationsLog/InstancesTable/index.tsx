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
} from '@camunda/camunda-api-zod-schemas/8.10/audit-log';
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
} from '@camunda/camunda-api-zod-schemas/8.10';
import {formatToISO} from 'modules/utils/date/formatDate';
import {PaginatedSortableTable} from 'modules/components/PaginatedSortableTable';
import {CellEntityKey} from './Cell/CellEntityKey';
import {CellResult} from './Cell/CellResult';
import {CellParentEntity} from './Cell/CellParentEntity';
import {CellDetails} from './Cell/CellDetails';
import {CellActor} from './Cell/CellActor';
import {CellComment} from './Cell/CellComment';
import {useDecisionDefinitions} from 'modules/hooks/decisionDefinitions';

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
    header: 'Entity key',
    key: 'entityKey',
    sortKey: 'entityKey',
  },
  {
    header: 'Parent entity',
    key: 'parentEntity',
    isDisabled: true,
  },
  {
    header: 'Details',
    key: 'details',
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
    filterValues.tenantId === 'all' ? undefined : filterValues.tenantId;
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
        processDefinitionId:
          filterValues.processDefinitionId &&
          (!filterValues.processDefinitionVersion ||
            filterValues.processDefinitionVersion === 'all')
            ? filterValues.processDefinitionId
            : undefined,
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

  const {data: decisionDefinitionNameArray} =
    useDecisionDefinitions(selectedTenantId);

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
      data?.auditLogs.map((item: AuditLog) => {
        const processDefinitionName = item.processDefinitionKey
          ? processDefinitionNameMap?.[item.processDefinitionKey]
          : undefined;
        const decisionDefinitionName =
          item.decisionDefinitionKey && decisionDefinitionNameArray
            ? decisionDefinitionNameArray.find(
                (dd) => dd.decisionDefinitionKey === item.decisionDefinitionKey,
              )?.name
            : undefined;
        return {
          id: item.auditLogKey,
          result: <CellResult item={item} />,
          operationType: spaceAndCapitalize(item.operationType),
          entityType: spaceAndCapitalize(item.entityType),
          entityKey: (
            <CellEntityKey
              item={item}
              processDefinitionName={processDefinitionName}
              decisionDefinitionName={decisionDefinitionName}
            />
          ),
          parentEntity: (
            <CellParentEntity
              item={item}
              processDefinitionName={processDefinitionName}
            />
          ),
          details: <CellDetails item={item} />,
          user: <CellActor item={item} />,
          timestamp: formatDate(item.timestamp),
          comment: (
            <CellComment item={item} setDetailsModal={setDetailsModal} />
          ),
        };
      }) || [],
    [data, processDefinitionNameMap, decisionDefinitionNameArray],
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

  const emptyMessage = useMemo(() => {
    if (Object.keys(filterValues).length === 0) {
      return {
        message: 'No operation log items yet',
        additionalInfo:
          'Operations that you perform in UI or via the API will appear here.',
      };
    }
    return {
      message: 'No operations log found',
      additionalInfo: 'Try adjusting your filters or check back later.',
    };
  }, [filterValues]);

  return (
    <Container>
      <BasePanelHeader title="Operations Log" count={data?.totalCount} />
      <PaginatedSortableTable
        state={getTableState()}
        rows={rows}
        emptyMessage={emptyMessage}
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
