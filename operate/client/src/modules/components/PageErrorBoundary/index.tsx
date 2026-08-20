/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isRouteErrorResponse, useRouteError} from 'react-router-dom';
import {CodeSnippet} from '@carbon/react';
import {ErrorWrapper} from './ErrorWrapper';

const PageErrorBoundary: React.FC = () => {
  const error = useRouteError();

  if (isRouteErrorResponse(error)) {
    return (
      <ErrorWrapper>
        <p>
          {error.status} {error.statusText}
        </p>
        <CodeSnippet type="multi">{error.data}</CodeSnippet>
      </ErrorWrapper>
    );
  }
  if (error instanceof Error) {
    const stackTrace = error.stack ?? 'No stack trace available';
    return (
      <ErrorWrapper>
        <CodeSnippet>{error.message}</CodeSnippet>
        <h4>Error stack trace</h4>
        <CodeSnippet type="multi">{stackTrace}</CodeSnippet>
      </ErrorWrapper>
    );
  }
  return (
    <ErrorWrapper>
      <CodeSnippet type="single">Unknown error</CodeSnippet>
    </ErrorWrapper>
  );
};

export {PageErrorBoundary};
