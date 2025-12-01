/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {OperationItems} from 'modules/components/OperationItems';
import {OperationItem} from 'modules/components/OperationItem';
import {tracking} from 'modules/tracking';
import {InlineLoading} from '@carbon/react';
import {Container} from './styled';
import {handleOperationError} from 'modules/utils/notifications';
import {useResolveIncident} from 'modules/mutations/incidents/useResolveIncident';

type IncidentOperationProps = {
  incidentKey: string;
  jobKey?: string;
};

const IncidentOperation: React.FC<IncidentOperationProps> = (props) => {
  const {isPending, mutate: resolveIncident} = useResolveIncident({
    incidentKey: props.incidentKey,
    jobKey: props.jobKey,
    onError: (error) => {
      handleOperationError(error.status);
    },
    onSuccess: () => {
      tracking.track({
        eventName: 'single-operation',
        operationType: 'RESOLVE_INCIDENT',
        source: 'incident-table',
      });
    },
  });

  const handleClick: React.MouseEventHandler<HTMLButtonElement> = (e) => {
    e.stopPropagation();
    resolveIncident();
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

export {IncidentOperation};
