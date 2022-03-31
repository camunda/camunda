/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observer} from 'mobx-react';
import {decisionInstanceStore} from 'modules/stores/decisionInstance';
import {drdStore} from 'modules/stores/drd';
import {Container, EvaluatedIcon, FailedIcon, SkeletonCircle} from './styled';
import {Button} from 'modules/components/Button';
import {Details} from './Details';
import {tracking} from 'modules/tracking';

const Header: React.FC = observer(() => {
  const {
    state: {status, decisionInstance},
  } = decisionInstanceStore;

  return (
    <Container data-testid="decision-instance-header">
      {status === 'initial' && (
        <>
          <SkeletonCircle />
          <Details data-testid="details-skeleton" />
        </>
      )}
      {status === 'fetched' && decisionInstance !== null && (
        <>
          {decisionInstance.state === 'FAILED' && (
            <FailedIcon data-testid="failed-icon" />
          )}
          {decisionInstance.state === 'EVALUATED' && (
            <EvaluatedIcon data-testid="evaluated-icon" />
          )}
          <Details decisionInstance={decisionInstance} />
        </>
      )}
      <Button
        size="small"
        color="primary"
        title="Open Decision Requirements Diagram"
        onClick={() => {
          drdStore.setPanelState('minimized');
          tracking.track({
            eventName: 'drd-panel-interaction',
            action: 'open',
          });
        }}
        disabled={status === 'initial'}
      >
        Open DRD
      </Button>
    </Container>
  );
});

export {Header};
