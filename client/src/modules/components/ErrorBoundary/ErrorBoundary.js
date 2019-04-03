/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      error: null
    };
  }

  componentDidCatch(error) {
    this.setState({error});
  }

  render() {
    const {error} = this.state;

    if (error) {
      return (
        <div>
          <h1>Oh no :(</h1>
          <pre>{error.message || error}</pre>
        </div>
      );
    }
    return this.props.children;
  }
}
