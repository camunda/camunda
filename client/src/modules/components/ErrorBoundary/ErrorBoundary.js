/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {ErrorPage} from 'components';

export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      error: null,
    };
  }

  componentDidCatch(error) {
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
          <pre>{error.message || error}</pre>
        </ErrorPage>
      );
    }
    return this.props.children;
  }
}
