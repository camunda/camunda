/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {formatDate} from 'modules/utils/date';
import {getProcessName} from 'modules/utils/instance';
import {Operations} from 'modules/components/Carbon/Operations';
import {observer} from 'mobx-react';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {variablesStore} from 'modules/stores/variables';
import {useNotifications} from 'modules/notifications';
import {Link} from 'modules/components/Carbon/Link';
import {CarbonLocations, CarbonPaths} from 'modules/carbonRoutes';
import {Restricted} from 'modules/components/Restricted';
import {panelStatesStore} from 'modules/stores/panelStates';
import {modificationsStore} from 'modules/stores/modifications';
import {tracking} from 'modules/tracking';
import {InstanceHeader} from 'modules/components/Carbon/InstanceHeader';
import {Skeleton} from 'modules/components/Carbon/InstanceHeader/Skeleton';

const headerColumns = [
  {
    name: 'Process Name',
    skeletonWidth: '94px',
  },
  {
    name: 'Process Instance Key',
    skeletonWidth: '136px',
  },
  {
    name: 'Version',
    skeletonWidth: '34px',
  },
  {
    name: 'Start Date',
    skeletonWidth: '142px',
  },
  {
    name: 'End Date',
    skeletonWidth: '142px',
  },
  {
    name: 'Parent Process Instance Key',
    skeletonWidth: '142px',
  },
  {
    name: 'Called Process Instances',
    skeletonWidth: '142px',
  },
];

const ProcessInstanceHeader: React.FC = observer(() => {
  const {processInstance} = processInstanceDetailsStore.state;
  const notifications = useNotifications();

  if (
    processInstance === null ||
    !processInstanceDetailsDiagramStore.areDiagramDefinitionsAvailable
  ) {
    return <Skeleton headerColumns={headerColumns} />;
  }

  const {
    id,
    processVersion,
    startDate,
    endDate,
    parentInstanceId,
    state,
    bpmnProcessId,
  } = processInstance;

  return (
    <InstanceHeader
      state={state}
      hideBottomBorder={state === 'INCIDENT'}
      headerColumns={headerColumns.map(({name}) => name)}
      bodyColumns={[
        {
          title: getProcessName(processInstance),
          content: getProcessName(processInstance),
        },
        {title: id, content: id},
        {
          content: (
            <Link
              to={CarbonLocations.processes({
                version: processVersion?.toString(),
                process: bpmnProcessId,
                active: true,
                incidents: true,
              })}
              title={`View process ${getProcessName(
                processInstance
              )} version ${processVersion} instances`}
              onClick={() => {
                tracking.track({
                  eventName: 'navigation',
                  link: 'process-details-version',
                });
              }}
            >
              {processVersion}
            </Link>
          ),
        },
        {
          title: formatDate(startDate) ?? '--',
          content: formatDate(startDate),
          dataTestId: 'start-date',
        },
        {
          title: formatDate(endDate) ?? '--',
          content: formatDate(endDate),
          dataTestId: 'end-date',
        },
        {
          title: parentInstanceId ?? 'None',
          content: (
            <>
              {parentInstanceId !== null ? (
                <Link
                  to={CarbonPaths.processInstance(parentInstanceId)}
                  title={`View parent instance ${parentInstanceId}`}
                  onClick={() => {
                    tracking.track({
                      eventName: 'navigation',
                      link: 'process-details-parent-details',
                    });
                  }}
                >
                  {parentInstanceId}
                </Link>
              ) : (
                'None'
              )}
            </>
          ),
        },
        {
          content: (
            <>
              {processInstanceDetailsDiagramStore.hasCalledProcessInstances ? (
                <Link
                  to={CarbonLocations.processes({
                    parentInstanceId: id,
                    active: true,
                    incidents: true,
                    canceled: true,
                    completed: true,
                  })}
                  onClick={() => {
                    panelStatesStore.expandFiltersPanel();
                    tracking.track({
                      eventName: 'navigation',
                      link: 'process-details-called-instances',
                    });
                  }}
                  title="View all called instances"
                >
                  View All
                </Link>
              ) : (
                'None'
              )}
            </>
          ),
        },
      ]}
      additionalContent={
        <Restricted
          scopes={['write']}
          resourceBasedRestrictions={{
            scopes: ['UPDATE_PROCESS_INSTANCE', 'DELETE_PROCESS_INSTANCE'],
            permissions: processInstanceDetailsStore.getPermissions(),
          }}
        >
          <>
            <Operations
              instance={processInstance}
              onOperation={(operationType: OperationEntityType) =>
                processInstanceDetailsStore.activateOperation(operationType)
              }
              onError={({operationType, statusCode}) => {
                processInstanceDetailsStore.deactivateOperation(operationType);

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
                  source: 'instance-header',
                });
              }}
              forceSpinner={
                variablesStore.hasActiveOperation ||
                processInstance?.hasActiveOperation
              }
              isInstanceModificationVisible={
                !modificationsStore.isModificationModeEnabled
              }
              permissions={processInstanceDetailsStore.getPermissions()}
            />
          </>
        </Restricted>
      }
    />
  );
});

export {ProcessInstanceHeader};
