/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {formatDate} from 'modules/utils/date';
import {getProcessName} from 'modules/utils/instance';
import {Operations} from 'modules/components/Operations';
import Skeleton from './Skeleton';
import {observer} from 'mobx-react';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import * as Styled from './styled';
import {variablesStore} from 'modules/stores/variables';
import {useNotifications} from 'modules/notifications';
import {Link} from 'modules/components/Link';
import {Locations, Paths} from 'modules/routes';
import {Restricted} from 'modules/components/Restricted';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';

const ProcessInstanceHeader: React.FC = observer(() => {
  const {processInstance} = processInstanceDetailsStore.state;
  const notifications = useNotifications();

  if (
    processInstance === null ||
    !processInstanceDetailsDiagramStore.areDiagramDefinitionsAvailable
  ) {
    return (
      <Styled.Container>
        <Skeleton />
      </Styled.Container>
    );
  }

  const {id, processVersion, startDate, endDate, parentInstanceId, state} =
    processInstance;

  return (
    <Styled.Container data-testid="instance-header">
      <Styled.StateIconWrapper>
        <Styled.StateIcon state={state} />
      </Styled.StateIconWrapper>

      <Styled.Table>
        <thead>
          <tr>
            <Styled.Th>Process</Styled.Th>
            <Styled.Th>Instance Id</Styled.Th>
            <Styled.Th>Version</Styled.Th>
            <Styled.Th>Start Date</Styled.Th>
            <Styled.Th>End Date</Styled.Th>
            <Styled.Th>Parent Instance Id</Styled.Th>
            <Styled.Th>Called Instances</Styled.Th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <Styled.Td title={getProcessName(processInstance)}>
              {getProcessName(processInstance)}
            </Styled.Td>
            <Styled.Td title={id}>{id}</Styled.Td>
            <Styled.Td>{processVersion}</Styled.Td>
            <Styled.Td
              title={formatDate(startDate) ?? '--'}
              data-testid="start-date"
            >
              {formatDate(startDate)}
            </Styled.Td>
            <Styled.Td
              title={formatDate(endDate) ?? '--'}
              data-testid="end-date"
            >
              {formatDate(endDate)}
            </Styled.Td>
            <Styled.Td title={parentInstanceId ?? 'None'}>
              {parentInstanceId !== null ? (
                <Link
                  to={Paths.processInstance(parentInstanceId)}
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
            </Styled.Td>
            <Styled.Td>
              {processInstanceDetailsDiagramStore.hasCalledProcessInstances ? (
                <Link
                  to={Locations.processes({
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
            </Styled.Td>
          </tr>
        </tbody>
      </Styled.Table>
      <Restricted scopes={['write']}>
        <Operations
          instance={processInstance}
          onOperation={(operationType: OperationEntityType) =>
            processInstanceDetailsStore.activateOperation(operationType)
          }
          onError={(operationType) => {
            processInstanceDetailsStore.deactivateOperation(operationType);
            notifications.displayNotification('error', {
              headline: 'Operation could not be created',
            });
          }}
          forceSpinner={
            variablesStore.hasActiveOperation ||
            processInstance?.hasActiveOperation
          }
        />
      </Restricted>
    </Styled.Container>
  );
});

export {ProcessInstanceHeader};
