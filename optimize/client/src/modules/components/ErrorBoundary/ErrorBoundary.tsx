/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Component, ReactNode} from 'react';
import {ErrorPage} from 'components';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  error: Error | string | null;
}

export default class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);

    this.state = {
      error: null,
    };
  }

  componentDidCatch(error: Error) {
    this.setState({error});
  }

  render() {
    const {error} = this.state;

    if (error) {
      return (
        <ErrorPage
          noLink
          text={`An application error occured.
Refresh your browser or close it and sign in again.`}
        >
          <pre>{typeof error === 'object' ? error.message : error}</pre>
        </ErrorPage>
      );
    }
    return this.props.children;
  }
}
