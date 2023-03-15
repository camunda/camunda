/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useLocation} from 'react-router-dom';
import {observer} from 'mobx-react';

import {
  processInstancesStore,
  MAX_PROCESS_INSTANCES_STORED,
} from 'modules/stores/processInstances';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {authenticationStore} from 'modules/stores/authentication';

import {useFilters} from 'modules/hooks/useFilters';
import {useNotifications} from 'modules/notifications';
import {Paths} from 'modules/routes';
import {tracking} from 'modules/tracking';
import {getProcessInstanceFilters} from 'modules/utils/filter';
import {getProcessName} from 'modules/utils/instance';
import {formatDate} from 'modules/utils/date';

import {SortableTable} from 'modules/components/SortableTable';
import {SkeletonCheckboxBlock} from 'modules/components/SortableTable/Skeleton';
import {Operations} from 'modules/components/Operations';
import {Link} from 'modules/components/Link';

import {ProcessContainer, CircleBlock, ProcessBlock, State} from './styled';
import {processesStore} from 'modules/stores/processes';

type SkeletonColumns = React.ComponentProps<
  typeof SortableTable
>['skeletonColumns'];
function getSkeletonColumns(showOperationsColumn: boolean): SkeletonColumns {
  const INDEPENDENT_SKELETON_COLUMNS: SkeletonColumns = [
    {
      variant: 'custom',
      customSkeleton: (
        <ProcessContainer>
          <SkeletonCheckboxBlock />
          <CircleBlock />
          <ProcessBlock />
        </ProcessContainer>
      ),
    },
    {variant: 'block', width: '162px'},
    {variant: 'block', width: '17px'},
    {variant: 'block', width: '151px'},
    {variant: 'block', width: '151px'},
    {variant: 'block', width: '151px'},
  ];

  if (showOperationsColumn) {
    return [
      ...INDEPENDENT_SKELETON_COLUMNS,
      {
        variant: 'circle',
        width: '50px',
        height: '20px',
      },
    ];
  }

  return INDEPENDENT_SKELETON_COLUMNS;
}

const ROW_HEIGHT = 38;

const List: React.FC = observer(() => {
  const filters = useFilters();
  const location = useLocation();
  const notifications = useNotifications();

  const {
    areProcessInstancesEmpty,
    state: {status, processInstances},
  } = processInstancesStore;

  const {canceled, completed} = getProcessInstanceFilters(location.search);
  const listHasFinishedInstances = canceled || completed;

  const getEmptyListMessage = () => {
    return `There are no Instances matching this filter set${
      filters.areProcessInstanceStatesApplied()
        ? ''
        : '\n To see some results, select at least one Instance state'
    }`;
  };

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

  return (
    <SortableTable
      state={getTableState()}
      selectionType={
        authenticationStore.hasPermission(['write']) ? 'checkbox' : 'none'
      }
      checkIsAllSelected={() =>
        processInstancesSelectionStore.state.isAllChecked
      }
      onSelectAll={processInstancesSelectionStore.selectAllProcessInstances}
      headerColumns={[
        {
          content: 'Name',
          sortKey: 'processName',
        },
        {content: 'Process Instance Key', sortKey: 'id'},
        {
          content: 'Version',
          sortKey: 'processVersion',
        },
        {content: 'Start Date', sortKey: 'startDate', isDefault: true},
        {
          content: 'End Date',
          sortKey: 'endDate',
          isDisabled: !listHasFinishedInstances,
        },
        {
          content: 'Parent Process Instance Key',
          sortKey: 'parentInstanceId',
        },

        ...(authenticationStore.hasPermission(['write'])
          ? [
              {
                content: 'Operations',
                showExtraPadding: true,
                paddingWidth: 41,
              },
            ]
          : []),
      ]}
      skeletonColumns={getSkeletonColumns(
        authenticationStore.hasPermission(['write'])
      )}
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
              ROW_HEIGHT
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
          ariaLabel: `Instance ${instance.id}`,
          content: [
            {
              cellContent: (
                <>
                  <State
                    state={instance.state}
                    data-testid={`${instance.state}-icon-${instance.id}`}
                  />
                  <span>{getProcessName(instance)}</span>
                </>
              ),
            },
            {
              cellContent: (
                <Link
                  to={Paths.processInstance(instance.id)}
                  title={`View instance ${instance.id}`}
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
            },
            {
              cellContent: instance.processVersion,
            },
            {
              cellContent: formatDate(instance.startDate),
            },
            {
              cellContent: formatDate(instance.endDate),
              dataTestId: 'end-time',
            },
            {
              cellContent:
                instance.parentInstanceId !== null ? (
                  <Link
                    to={Paths.processInstance(instance.parentInstanceId)}
                    title={`View parent instance ${instance.parentInstanceId}`}
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
                ),
            },
            ...(authenticationStore.hasPermission(['write'])
              ? [
                  {
                    cellContent: (
                      <Operations
                        instance={instance}
                        onOperation={(operationType) =>
                          processInstancesStore.markProcessInstancesWithActiveOperations(
                            {
                              ids: [instance.id],
                              operationType,
                            }
                          )
                        }
                        onError={({operationType, statusCode}) => {
                          processInstancesStore.unmarkProcessInstancesWithActiveOperations(
                            {
                              instanceIds: [instance.id],
                              operationType,
                            }
                          );
                          notifications.displayNotification('error', {
                            headline: 'Operation could not be created',
                            description:
                              statusCode === 403
                                ? 'You do not have permission'
                                : undefined,
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
                          instance.bpmnProcessId
                        )}
                      />
                    ),
                  },
                ]
              : []),
          ],
          checkIsSelected: () => {
            return processInstancesSelectionStore.isProcessInstanceChecked(
              instance.id
            );
          },
          onSelect: () => {
            return processInstancesSelectionStore.selectProcessInstance(
              instance.id
            );
          },
        };
      })}
    />
  );
});

export {List};
