/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observer} from 'mobx-react';
import {StatusMessage} from 'modules/components/StatusMessage';
import {decisionInstanceStore} from 'modules/stores/decisionInstance';
import {JSONViewer} from './JSONViewer/index';
import {SpinnerSkeleton} from 'modules/components/SpinnerSkeleton';
import {Container} from './styled';

const Result: React.FC = observer(() => {
  const {
    state: {status, decisionInstance},
  } = decisionInstanceStore;

  return (
    <Container>
      {status === 'initial' && (
        <SpinnerSkeleton data-testid="result-loading-spinner" />
      )}
      {status === 'fetched' && decisionInstance !== null && (
        <JSONViewer
          value={decisionInstance.result}
          data-testid="results-json-viewer"
        />
      )}
      {status === 'error' && (
        <StatusMessage variant="error">Data could not be fetched</StatusMessage>
      )}
    </Container>
  );
});

export {Result};
