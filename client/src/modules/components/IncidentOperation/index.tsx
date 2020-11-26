/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState} from 'react';

import {OPERATION_TYPE} from 'modules/constants';
import {OperationSpinner} from 'modules/components/OperationSpinner';
import {operationsStore} from 'modules/stores/operations';
import {useNotifications} from 'modules/notifications';

import OperationItems from 'modules/components/OperationItems';
import {observer} from 'mobx-react';

import * as Styled from './styled';

type Props = {
  incident: unknown;
  instanceId: string;
  showSpinner?: boolean;
};

const IncidentOperation: React.FC<Props> = observer(
  ({instanceId, incident, showSpinner}) => {
    const [isSpinnerVisible, setIsSpinnerVisible] = useState(false);
    const notifications = useNotifications();

    const handleError = () => {
      setIsSpinnerVisible(false);
      notifications.displayNotification('error', {
        headline: 'Operation could not be created',
      });
    };

    const handleOnClick = async (e: any) => {
      e.stopPropagation();
      setIsSpinnerVisible(true);

      // incidents operations should listen to main btn who publishes the incident ids which are affected
      operationsStore.applyOperation({
        instanceId,
        payload: {
          operationType: OPERATION_TYPE.RESOLVE_INCIDENT,
          // @ts-expect-error
          incidentId: incident.id,
        },
        onError: handleError,
      });
    };

    return (
      <Styled.Operations>
        {(isSpinnerVisible || showSpinner) && (
          <OperationSpinner data-testid="operation-spinner" />
        )}
        <OperationItems>
          <OperationItems.Item
            type={OPERATION_TYPE.RESOLVE_INCIDENT}
            onClick={handleOnClick}
            data-testid="retry-incident"
            title="Retry Incident"
          />
        </OperationItems>
      </Styled.Operations>
    );
  }
);

export {IncidentOperation};
