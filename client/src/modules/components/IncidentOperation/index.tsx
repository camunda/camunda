/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';

import {OperationSpinner} from 'modules/components/OperationSpinner';
import {ErrorHandler, operationsStore} from 'modules/stores/operations';
import {useNotifications} from 'modules/notifications';

import {OperationItems} from 'modules/components/OperationItems';
import {OperationItem} from 'modules/components/OperationItem';
import {observer} from 'mobx-react';

import * as Styled from './styled';
import {tracking} from 'modules/tracking';

type Props = {
  incident: unknown;
  instanceId: string;
  showSpinner?: boolean;
};

const IncidentOperation: React.FC<Props> = observer(
  ({instanceId, incident, showSpinner}) => {
    const [hasActiveOperation, setHasActiveOperation] = useState(false);
    const notifications = useNotifications();

    const handleError: ErrorHandler = ({statusCode}) => {
      setHasActiveOperation(false);
      notifications.displayNotification('error', {
        headline: 'Operation could not be created',
        description:
          statusCode === 403 ? 'You do not have permission' : undefined,
      });
    };

    const handleOnClick = async (e: any) => {
      e.stopPropagation();
      setHasActiveOperation(true);

      // incidents operations should listen to main btn who publishes the incident ids which are affected
      operationsStore.applyOperation({
        instanceId,
        payload: {
          operationType: 'RESOLVE_INCIDENT',
          // @ts-expect-error
          incidentId: incident.id,
        },
        onError: handleError,
        onSuccess: () => {
          tracking.track({
            eventName: 'single-operation',
            operationType: 'RESOLVE_INCIDENT',
            source: 'incident-table',
          });
        },
      });
    };

    return (
      <Styled.Operations>
        {(hasActiveOperation || showSpinner) && (
          <OperationSpinner data-testid="operation-spinner" />
        )}
        <OperationItems>
          <OperationItem
            type="RESOLVE_INCIDENT"
            onClick={handleOnClick}
            data-testid="retry-incident"
            title="Retry Incident"
            disabled={hasActiveOperation || showSpinner}
          />
        </OperationItems>
      </Styled.Operations>
    );
  }
);

export {IncidentOperation};
