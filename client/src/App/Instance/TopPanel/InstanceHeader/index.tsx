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
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import * as Styled from './styled';
import {variablesStore} from 'modules/stores/variables';
import {useNotifications} from 'modules/notifications';
import {Link} from 'modules/components/Link';
import {Locations} from 'modules/routes';
import {Restricted} from 'modules/components/Restricted';
import {panelStatesStore} from 'modules/stores/panelStates';
import {tracking} from 'modules/tracking';
import {useLocation} from 'react-router-dom';

const InstanceHeader = observer(() => {
  const {instance} = currentInstanceStore.state;
  const notifications = useNotifications();
  const location = useLocation();

  if (
    instance === null ||
    !singleInstanceDiagramStore.areDiagramDefinitionsAvailable
  ) {
    return (
      <Styled.Container>
        <Skeleton />
      </Styled.Container>
    );
  }

  const {id, processVersion, startDate, endDate, parentInstanceId, state} =
    instance;

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
            <Styled.Td>{getProcessName(instance)}</Styled.Td>
            <Styled.Td>{id}</Styled.Td>
            <Styled.Td>{processVersion}</Styled.Td>
            <Styled.Td data-testid="start-date">
              {formatDate(startDate)}
            </Styled.Td>
            <Styled.Td data-testid="end-date">{formatDate(endDate)}</Styled.Td>
            <Styled.Td>
              {parentInstanceId !== null ? (
                <Link
                  to={Locations.instance(location, parentInstanceId)}
                  title={`View parent instance ${parentInstanceId}`}
                  onClick={() => {
                    tracking.track({
                      eventName: 'navigation',
                      link: 'instance-parent-details',
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
              {singleInstanceDiagramStore.hasCalledInstances ? (
                <Link
                  to={Locations.filters(location, {
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
                      link: 'instance-called-instances',
                    });
                  }}
                  title={`View all called instances`}
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
          instance={instance}
          onOperation={(operationType: OperationEntityType) =>
            currentInstanceStore.activateOperation(operationType)
          }
          onError={(operationType) => {
            currentInstanceStore.deactivateOperation(operationType);
            notifications.displayNotification('error', {
              headline: 'Operation could not be created',
            });
          }}
          forceSpinner={
            variablesStore.hasActiveOperation || instance?.hasActiveOperation
          }
        />
      </Restricted>
    </Styled.Container>
  );
});

export {InstanceHeader};
