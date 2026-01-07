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
import {Container, ProcessName} from '../../InstancesTable/styled';
import {observer} from 'mobx-react';
import {Paths} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {Link} from 'modules/components/Link';
import {useFilters} from 'modules/hooks/useFilters';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {batchModificationStore} from 'modules/stores/batchModification';
import {Toolbar} from './Toolbar';
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {useLocation, useSearchParams} from 'react-router-dom';
import {BatchModificationFooter} from './BatchModificationFooter';
import type {InstanceEntityState} from 'modules/types/operate';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelectionV2';
import {InstanceOperations} from './InstanceOperations';
import {getProcessDefinitionName} from 'modules/utils/instance';

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

    const isTenantColumnVisible =
      window.clientConfig?.multiTenancyEnabled &&
      (tenant === undefined || tenant === 'all');

    const batchOperationId = searchParams.get('operationId') ?? undefined;

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
          checkIsAllSelected={() =>
            processInstancesSelectionStore.state.isAllChecked
          }
          checkIsIndeterminate={() =>
            !processInstancesSelectionStore.state.isAllChecked &&
            processInstancesSelectionStore.selectedProcessInstanceCount > 0
          }
          checkIsRowSelected={(rowId) => {
            return processInstancesSelectionStore.checkedProcessInstanceIds.includes(
              rowId,
            );
          }}
          emptyMessage={getEmptyListMessage()}
          onVerticalScrollStartReach={onVerticalScrollStartReach}
          onVerticalScrollEndReach={onVerticalScrollEndReach}
          rows={processInstances.map((instance) => {
            const instanceState: InstanceEntityState = instance.hasIncident
              ? 'INCIDENT'
              : instance.state;

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
