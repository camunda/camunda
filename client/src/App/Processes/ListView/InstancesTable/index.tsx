/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import {getProcessInstanceFilters} from 'modules/utils/filter';
import {useLocation} from 'react-router-dom';
import {processesStore} from 'modules/stores/processes';
import {Operations} from 'modules/components/Operations';
import {notificationsStore} from 'modules/stores/notifications';

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
        selectedInstancesCount={processInstancesSelectionStore.getSelectedProcessInstanceCount()}
      />
      <SortableTable
        state={getTableState()}
        columnsWithNoContentPadding={['operations']}
        selectionType={
          authenticationStore.hasPermission(['write']) ? 'checkbox' : 'none'
        }
        onSelectAll={
          processInstancesSelectionStore.selectAllProcessInstancesCarbon
        }
        onSelect={(rowId) => {
          return processInstancesSelectionStore.selectProcessInstanceCarbon(
            rowId,
          );
        }}
        checkIsAllSelected={() =>
          processInstancesSelectionStore.state.isAllChecked
        }
        checkIsIndeterminate={() =>
          !processInstancesSelectionStore.state.isAllChecked &&
          processInstancesSelectionStore.getSelectedProcessInstanceCount() > 0
        }
        checkIsRowSelected={(rowId) => {
          return processInstancesSelectionStore.isProcessInstanceChecked(rowId);
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
    </Container>
  );
});

export {InstancesTable};
