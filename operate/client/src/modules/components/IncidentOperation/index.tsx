/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useState} from 'react';

import {type ErrorHandler, operationsStore} from 'modules/stores/operations';
import {OperationItems} from 'modules/components/OperationItems';
import {OperationItem} from 'modules/components/OperationItem';
import {observer} from 'mobx-react';

import {tracking} from 'modules/tracking';
import {InlineLoading} from '@carbon/react';
import {Container} from './styled';
import {notificationsStore} from 'modules/stores/notifications';
import {useResolveIncident} from 'modules/mutations/incidents/useResolveIncident';

type Props = {
  incidentKey: string;
  instanceId: string;
  showSpinner?: boolean;
};

const IncidentOperation: React.FC<Props> = observer(
  ({instanceId, incidentKey, showSpinner}) => {
    const [hasActiveOperation, setHasActiveOperation] = useState(false);

    const handleError: ErrorHandler = ({statusCode}) => {
      setHasActiveOperation(false);
      notificationsStore.displayNotification({
        kind: 'error',
        title: 'Operation could not be created',
        subtitle: statusCode === 403 ? 'You do not have permission' : undefined,
        isDismissable: true,
      });
    };

    const handleOnClick = async (e: React.MouseEvent<HTMLButtonElement>) => {
      e.stopPropagation();
      setHasActiveOperation(true);

      operationsStore.applyOperation({
        instanceId,
        payload: {
          operationType: 'RESOLVE_INCIDENT',
          incidentId: incidentKey,
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
  },
);

type IncidentOperationProps = {
  incidentKey: string;
  jobKey?: string;
};

const IncidentOperationV2: React.FC<IncidentOperationProps> = (props) => {
  const {isPending, mutate} = useResolveIncident(
    props.incidentKey,
    props.jobKey,
  );

  const handleClick: React.MouseEventHandler<HTMLButtonElement> = (e) => {
    e.stopPropagation();
    mutate(undefined, {
      onError: (error) => {
        notificationsStore.displayNotification({
          kind: 'error',
          title: 'Incident could not be resolved',
          subtitle:
            error.status === 403 ? 'You do not have permission' : undefined,
          isDismissable: true,
        });
      },
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
      {isPending && <InlineLoading data-testid="operation-spinner" />}
      <OperationItems>
        <OperationItem
          type="RESOLVE_INCIDENT"
          onClick={handleClick}
          data-testid="retry-incident"
          title="Retry Incident"
          disabled={isPending}
          size="sm"
        />
      </OperationItems>
    </Container>
  );
};

export {IncidentOperation, IncidentOperationV2};
