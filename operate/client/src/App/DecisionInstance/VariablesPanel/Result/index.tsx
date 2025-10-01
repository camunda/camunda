/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Container} from './styled';
import {EmptyMessage} from 'modules/components/EmptyMessage';
import {ErrorMessage} from 'modules/components/ErrorMessage';
import {Loading} from '@carbon/react';
import {lazy, Suspense} from 'react';
import type {DecisionInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {useDecisionInstance} from 'modules/queries/decisionInstances/useDecisionInstance';

const JSONViewer = lazy(async () => {
  const {JSONViewer} = await import('./JSONViewer');
  return {default: JSONViewer};
});

type ResultProps = {
  decisionEvaluationInstanceKey: DecisionInstance['decisionEvaluationInstanceKey'];
};

const Result: React.FC<ResultProps> = ({decisionEvaluationInstanceKey}) => {
  const {data: decisionInstance, status} = useDecisionInstance(
    decisionEvaluationInstanceKey,
  );

  return (
    <Container>
      {status === 'pending' && <Loading data-testid="result-loading-spinner" />}
      {status === 'success' &&
        decisionInstance !== null &&
        decisionInstance.state !== 'FAILED' && (
          <Suspense>
            <JSONViewer
              data-testid="results-json-viewer"
              value={decisionInstance.result ?? '{}'}
            />
          </Suspense>
        )}
      {status === 'success' && decisionInstance?.state === 'FAILED' && (
        <EmptyMessage message="No result available because the evaluation failed" />
      )}
      {status === 'error' && <ErrorMessage />}
    </Container>
  );
};

export {Result};
