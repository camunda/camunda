/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {Container} from './styled';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {ErrorMessage} from 'modules/components/ErrorMessage';
import {Loading} from '@carbon/react';
import {lazy, Suspense} from 'react';

const JSONViewer = lazy(async () => {
  const {JSONViewer} = await import('./JSONViewer');
  return {default: JSONViewer};
});

const Result: React.FC = observer(() => {
  const {
    state: {status, decisionInstance},
  } = decisionInstanceDetailsStore;

  return (
    <Container>
      {status === 'initial' && <Loading data-testid="result-loading-spinner" />}
      {status === 'fetched' &&
        decisionInstance !== null &&
        decisionInstance.state !== 'FAILED' && (
          <Suspense>
            <JSONViewer
              data-testid="results-json-viewer"
              value={decisionInstance.result ?? '{}'}
            />
          </Suspense>
        )}
      {status === 'fetched' && decisionInstance?.state === 'FAILED' && (
        <EmptyMessage message="No result available because the evaluation failed" />
      )}
      {status === 'error' && <ErrorMessage />}
    </Container>
  );
});

export {Result};
