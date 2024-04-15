/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
import {
  MAX_PROCESS_INSTANCES_STORED,
  processInstancesStore,
} from 'modules/stores/processInstances';
import {getProcessName} from 'modules/utils/instance';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {authenticationStore} from 'modules/stores/authentication';
import {Toolbar} from './Toolbar';
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {useLocation} from 'react-router-dom';
import {processesStore} from 'modules/stores/processes/processes.list';
import {Operations} from 'modules/components/Operations';
import {notificationsStore} from 'modules/stores/notifications';
import {batchModificationStore} from 'modules/stores/batchModification';
import {BatchModificationFooter} from './BatchModificationFooter';
import {useEffect} from 'react';
import {processStatisticsBatchModificationStore} from 'modules/stores/processStatistics/processStatistics.batchModification';
import {getProcessInstancesRequestFilters} from 'modules/utils/filter';

const ROW_HEIGHT = 34;

const InstancesTable: React.FC = observer(() => {
  const {
    areProcessInstancesEmpty,
    state: {status, filteredProcessInstancesCount, processInstances},
  } = processInstancesStore;

  const filters = useFilters();
  const location = useLocation();

  const {canceled, completed, tenant} = getProcessInstanceFilters(
    location.search,
  );
  const listHasFinishedInstances = canceled || completed;

  const isTenantColumnVisible =
    window.clientConfig?.multiTenancyEnabled &&
    (tenant === undefined || tenant === 'all');

  const {batchOperationId} = getProcessInstancesRequestFilters();

  const isOperationStateColumnVisible = !!batchOperationId;

  const getTableState = () => {
    if (['initial', 'first-fetch'].includes(status)) {
      return 'skeleton';
    }
    if (status === 'fetching') {
      return 'loading';
    }
    if (status === 'error') {
      return 'error';
    }
    if (status === 'fetched' && areProcessInstancesEmpty) {
      return 'empty';
    }

    return 'content';
  };

  const {
    selectedProcessInstanceIds,
    excludedProcessInstanceIds,
    state: {selectionMode},
  } = processInstancesSelectionStore;

  const isBatchModificationEnabled = batchModificationStore.state.isEnabled;

  useEffect(() => {
    if (!isBatchModificationEnabled) {
      return;
    }

    if (
      selectionMode === 'INCLUDE' &&
      selectedProcessInstanceIds.length === 0
    ) {
      processStatisticsBatchModificationStore.setStatistics([]);
      return;
    }

    const processInstanceFilters = getProcessInstanceFilters(location.search);
    const filteredIds = processInstanceFilters.ids?.split(/[\s,]+/);
    const ids = ['EXCLUDE', 'ALL'].includes(selectionMode)
      ? filteredIds ?? []
      : selectedProcessInstanceIds;

    const requestFilterParameters = {
      ...processInstanceFilters,
      ids,
      excludeIds: excludedProcessInstanceIds,
      finished: false,
      completed: false,
      canceled: false,
    };

    processStatisticsBatchModificationStore.fetchProcessStatistics(
      requestFilterParameters,
    );
  }, [
    selectedProcessInstanceIds,
    excludedProcessInstanceIds,
    isBatchModificationEnabled,
    selectionMode,
    location.search,
  ]);

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
        count={filteredProcessInstancesCount}
      />

      <Toolbar
        selectedInstancesCount={
          processInstancesSelectionStore.selectedProcessInstanceCount
        }
      />
      <SortableTable
        state={getTableState()}
        columnsWithNoContentPadding={['operations']}
        selectionType={
          authenticationStore.hasPermission(['write']) ? 'checkbox' : 'none'
        }
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
          return processInstancesSelectionStore.isProcessInstanceChecked(rowId);
        }}
        rowOperationError={(rowId) => {
          return processInstancesStore.getProcessInstanceOperationError(rowId);
        }}
        emptyMessage={getEmptyListMessage()}
        onVerticalScrollStartReach={async (scrollDown) => {
          if (processInstancesStore.shouldFetchPreviousInstances() === false) {
            return;
          }

          await processInstancesStore.fetchPreviousInstances();

          if (
            processInstancesStore.state.processInstances.length ===
              MAX_PROCESS_INSTANCES_STORED &&
            processInstancesStore.state.latestFetch?.processInstancesCount !==
              0 &&
            processInstancesStore.state.latestFetch !== null
          ) {
            scrollDown(
              processInstancesStore.state.latestFetch.processInstancesCount *
                ROW_HEIGHT,
            );
          }
        }}
        onVerticalScrollEndReach={() => {
          if (processInstancesStore.shouldFetchNextInstances() === false) {
            return;
          }

          processInstancesStore.fetchNextInstances();
        }}
        rows={processInstances.map((instance) => {
          return {
            id: instance.id,
            processName: (
              <ProcessName>
                <StateIcon
                  state={instance.state}
                  data-testid={`${instance.state}-icon-${instance.id}`}
                  size={20}
                />

                {getProcessName(instance)}
              </ProcessName>
            ),
            instanceOperationState: isOperationStateColumnVisible
              ? instance.operations?.find(
                  (operation) =>
                    operation.batchOperationId === batchOperationId,
                )?.state || '--'
              : undefined,
            processInstanceKey: (
              <Link
                to={Paths.processInstance(instance.id)}
                title={`View instance ${instance.id}`}
                aria-label={`View instance ${instance.id}`}
                onClick={() => {
                  tracking.track({
                    eventName: 'navigation',
                    link: 'processes-instance-details',
                  });
                }}
              >
                {instance.id}
              </Link>
            ),
            processVersion: instance.processVersion,
            tenant: isTenantColumnVisible ? instance.tenantId : undefined,
            startDate: formatDate(instance.startDate),
            endDate: formatDate(instance.endDate),
            parentInstanceId: (
              <>
                {instance.parentInstanceId !== null ? (
                  <Link
                    to={Paths.processInstance(instance.parentInstanceId)}
                    title={`View parent instance ${instance.parentInstanceId}`}
                    aria-label={`View parent instance ${instance.parentInstanceId}`}
                    onClick={() => {
                      tracking.track({
                        eventName: 'navigation',
                        link: 'processes-parent-instance-details',
                      });
                    }}
                  >
                    {instance.parentInstanceId}
                  </Link>
                ) : (
                  'None'
                )}
              </>
            ),
            operations: authenticationStore.hasPermission(['write']) ? (
              <Operations
                instance={instance}
                onOperation={(operationType) =>
                  processInstancesStore.markProcessInstancesWithActiveOperations(
                    {
                      ids: [instance.id],
                      operationType,
                    },
                  )
                }
                onError={({operationType, statusCode}) => {
                  processInstancesStore.unmarkProcessInstancesWithActiveOperations(
                    {
                      instanceIds: [instance.id],
                      operationType,
                    },
                  );
                  notificationsStore.displayNotification({
                    kind: 'error',
                    title: 'Operation could not be created',
                    subtitle:
                      statusCode === 403
                        ? 'You do not have permission'
                        : undefined,
                    isDismissable: true,
                  });
                }}
                onSuccess={(operationType) => {
                  tracking.track({
                    eventName: 'single-operation',
                    operationType,
                    source: 'instances-list',
                  });
                }}
                permissions={processesStore.getPermissions(
                  instance.bpmnProcessId,
                  instance.tenantId,
                )}
              />
            ) : undefined,
          };
        })}
        headerColumns={[
          {
            header: 'Name',
            key: 'processName',
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
            sortKey: 'id',
          },
          {
            header: 'Version',
            key: 'processVersion',
          },
          ...(isTenantColumnVisible
            ? [
                {
                  header: 'Tenant',
                  key: 'tenant',
                },
              ]
            : []),
          {
            header: 'Start Date',
            key: 'startDate',
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
          },
          ...(authenticationStore.hasPermission(['write'])
            ? [
                {
                  header: 'Operations',
                  key: 'operations',
                  isDisabled: true,
                },
              ]
            : []),
        ]}
      />
      {batchModificationStore.state.isEnabled && <BatchModificationFooter />}
    </Container>
  );
});

export {InstancesTable};
