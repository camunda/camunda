/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {PanelHeader} from 'modules/components/PanelHeader';
import {SortableTable} from 'modules/components/SortableTable';
import {StateIcon} from 'modules/components/StateIcon';
import {formatDate} from 'modules/utils/date';
import {Container, ProcessName} from './styled';
import {observer} from 'mobx-react';
import {Paths} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {Link} from 'modules/components/Link';
import {useFilters} from 'modules/hooks/useFilters';
import type {
  ProcessInstance,
  BatchOperationItem,
  ProcessInstanceState,
  BatchOperationType,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {batchModificationStore} from 'modules/stores/batchModification';
import {Toolbar} from './Toolbar';
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {useLocation, useSearchParams} from 'react-router-dom';
import {BatchModificationFooter} from './BatchModificationFooter';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {InstanceOperations} from './InstanceOperations';
import {getProcessDefinitionName} from 'modules/utils/instance';
import {useOperationItemsForInstances} from 'modules/queries/batch-operations/useOperationItemsForInstances';
import {useActiveOperationItemsForInstances} from 'modules/queries/batch-operations/useActiveOperationItemsForInstances';
import {InlineLoading} from '@carbon/react';
import {getClientConfig} from 'modules/utils/getClientConfig';

type InstancesTableProps = {
  state: 'skeleton' | 'loading' | 'error' | 'empty' | 'content';
  processInstances: ProcessInstance[];
  totalProcessInstancesCount: number;
  onVerticalScrollStartReach?: (scrollDown: (offset: number) => void) => void;
  onVerticalScrollEndReach?: () => void;
};

const InstancesTable: React.FC<InstancesTableProps> = observer(
  ({
    state,
    processInstances,
    totalProcessInstancesCount,
    onVerticalScrollStartReach,
    onVerticalScrollEndReach,
  }) => {
    const hasVersionTags = processInstances.some(
      ({processDefinitionVersionTag}) => !!processDefinitionVersionTag,
    );

    const filters = useFilters();
    const location = useLocation();
    const [searchParams] = useSearchParams();

    const {canceled, completed, tenant} = getProcessInstanceFilters(
      location.search,
    );
    const listHasFinishedInstances = canceled || completed;
    const clientConfig = getClientConfig();

    const isTenantColumnVisible =
      clientConfig.multiTenancyEnabled &&
      (tenant === undefined || tenant === 'all');

    const batchOperationId = searchParams.get('operationId') ?? undefined;
    const isOperationStateColumnVisible = !!batchOperationId;

    const processInstanceKeys = processInstances.map(
      (instance) => instance.processInstanceKey,
    );

    const {data: operationItemsData, isLoading: isLoadingOperationItems} =
      useOperationItemsForInstances(batchOperationId, processInstanceKeys);

    const operationItemsMap = new Map<string, BatchOperationItem>();
    operationItemsData?.items.forEach((item) => {
      operationItemsMap.set(item.processInstanceKey, item);
    });

    const {data: activeOperationItemsData} =
      useActiveOperationItemsForInstances(processInstanceKeys);

    const activeOperationsMap = new Map<string, BatchOperationType[]>();
    activeOperationItemsData?.items.forEach((item) => {
      const existing = activeOperationsMap.get(item.processInstanceKey);
      if (existing) {
        existing.push(item.operationType);
      } else {
        activeOperationsMap.set(item.processInstanceKey, [item.operationType]);
      }
    });

    const getEmptyListMessage = () => {
      return {
        message: 'There are no Instances matching this filter set',
        additionalInfo: filters.areProcessInstanceStatesApplied()
          ? undefined
          : 'To see some results, select at least one Instance state',
      };
    };

    return (
      <Container aria-label="Process Instances Panel">
        <PanelHeader
          title="Process Instances"
          count={totalProcessInstancesCount}
        />
        <Toolbar
          selectedInstancesCount={
            processInstancesSelectionStore.selectedProcessInstanceCount
          }
        />
        <SortableTable
          state={state}
          columnsWithNoContentPadding={['operations']}
          selectionType="checkbox"
          onSelectAll={processInstancesSelectionStore.selectAllProcessInstances}
          onSelect={(rowId) => {
            processInstancesSelectionStore.selectProcessInstance(rowId);
          }}
          checkIsAllSelected={() => processInstancesSelectionStore.isAllChecked}
          checkIsIndeterminate={() =>
            !processInstancesSelectionStore.isAllChecked &&
            processInstancesSelectionStore.selectedProcessInstanceCount > 0
          }
          checkIsRowSelected={(rowId) => {
            return processInstancesSelectionStore.checkedProcessInstanceIds.includes(
              rowId,
            );
          }}
          rowOperationError={
            isOperationStateColumnVisible
              ? (rowId) => {
                  const operationItem = operationItemsMap.get(rowId);
                  return operationItem?.state === 'FAILED'
                    ? (operationItem.errorMessage ?? 'Operation failed')
                    : null;
                }
              : undefined
          }
          emptyMessage={getEmptyListMessage()}
          onVerticalScrollStartReach={onVerticalScrollStartReach}
          onVerticalScrollEndReach={onVerticalScrollEndReach}
          rows={processInstances.map((instance) => {
            const instanceState: ProcessInstanceState | 'INCIDENT' =
              instance.hasIncident ? 'INCIDENT' : instance.state;

            const operationItem = operationItemsMap.get(
              instance.processInstanceKey,
            );

            return {
              id: instance.processInstanceKey,
              processName: (
                <ProcessName>
                  <StateIcon
                    state={instanceState}
                    data-testid={`${instanceState}-icon-${instance.processInstanceKey}`}
                    size={20}
                  />

                  {getProcessDefinitionName(instance)}
                </ProcessName>
              ),
              processInstanceKey: (
                <Link
                  to={Paths.processInstance(instance.processInstanceKey)}
                  title={`View instance ${instance.processInstanceKey}`}
                  aria-label={`View instance ${instance.processInstanceKey}`}
                  onClick={() => {
                    tracking.track({
                      eventName: 'navigation',
                      link: 'processes-instance-details',
                    });
                  }}
                >
                  {instance.processInstanceKey}
                </Link>
              ),
              processVersion: instance.processDefinitionVersion,
              versionTag: instance.processDefinitionVersionTag ?? '--',
              tenant: isTenantColumnVisible ? instance.tenantId : undefined,
              ...(isOperationStateColumnVisible && {
                instanceOperationState: isLoadingOperationItems ? (
                  <InlineLoading description="Loading..." />
                ) : (
                  (operationItem?.state ?? '--')
                ),
              }),
              startDate: formatDate(instance.startDate),
              endDate: formatDate(instance.endDate ?? null),
              parentInstanceId: (
                <>
                  {instance.parentProcessInstanceKey ? (
                    <Link
                      to={Paths.processInstance(
                        instance.parentProcessInstanceKey,
                      )}
                      title={`View parent instance ${instance.parentProcessInstanceKey}`}
                      aria-label={`View parent instance ${instance.parentProcessInstanceKey}`}
                      onClick={() => {
                        tracking.track({
                          eventName: 'navigation',
                          link: 'processes-parent-instance-details',
                        });
                      }}
                    >
                      {instance.parentProcessInstanceKey}
                    </Link>
                  ) : (
                    'None'
                  )}
                </>
              ),
              operations: (
                <InstanceOperations
                  processInstanceKey={instance.processInstanceKey}
                  isInstanceActive={
                    instance.state === 'ACTIVE' || instance.hasIncident
                  }
                  hasIncident={instance.hasIncident}
                  activeOperations={
                    activeOperationsMap.get(instance.processInstanceKey) ?? []
                  }
                />
              ),
            };
          })}
          headerColumns={[
            {
              header: 'Name',
              key: 'processName',
              sortKey: 'processDefinitionName',
            },
            ...(isOperationStateColumnVisible
              ? [
                  {
                    header: 'Operation State',
                    key: 'instanceOperationState',
                  },
                ]
              : []),
            {
              header: 'Process Instance Key',
              key: 'processInstanceKey',
              sortKey: 'processInstanceKey',
            },
            {
              header: 'Version',
              key: 'processVersion',
              sortKey: 'processDefinitionVersion',
            },
            ...(hasVersionTags
              ? [
                  {
                    header: 'Version Tag',
                    key: 'versionTag',
                    isDisabled: true,
                  },
                ]
              : []),
            ...(isTenantColumnVisible
              ? [
                  {
                    header: 'Tenant',
                    key: 'tenant',
                    sortKey: 'tenantId',
                  },
                ]
              : []),
            {
              header: 'Start Date',
              key: 'startDate',
              sortKey: 'startDate',
              isDefault: true,
            },
            {
              header: 'End Date',
              key: 'endDate',
              isDisabled: !listHasFinishedInstances,
            },
            {
              header: 'Parent Process Instance Key',
              key: 'parentInstanceId',
              sortKey: 'parentProcessInstanceKey',
            },
            {
              header: 'Operations',
              key: 'operations',
              isDisabled: true,
            },
          ]}
          batchOperationId={batchOperationId}
        />
        {batchModificationStore.state.isEnabled && <BatchModificationFooter />}
      </Container>
    );
  },
);

export {InstancesTable};
