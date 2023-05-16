/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState} from 'react';

import {ErrorHandler, operationsStore} from 'modules/stores/operations';
import {useNotifications} from 'modules/notifications';

import {Incident} from 'modules/stores/incidents';
import {OperationItems} from 'modules/components/Carbon/OperationItems';
import {OperationItem} from 'modules/components/Carbon/OperationItem';
import {observer} from 'mobx-react';

import {tracking} from 'modules/tracking';
import {InlineLoading} from '@carbon/react';
import {Container} from './styled';

type Props = {
  incident: Incident;
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

      operationsStore.applyOperation({
        instanceId,
        payload: {
          operationType: 'RESOLVE_INCIDENT',
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
      <Container orientation="horizontal">
        {(hasActiveOperation || showSpinner) && (
          <InlineLoading data-testid="operation-spinner" />
        )}
        <OperationItems>
          <OperationItem
            type="RESOLVE_INCIDENT"
            onClick={handleOnClick}
            data-testid="retry-incident"
            title="Retry Incident"
            disabled={hasActiveOperation || showSpinner}
            size="sm"
          />
        </OperationItems>
      </Container>
    );
  }
);

export {IncidentOperation};
