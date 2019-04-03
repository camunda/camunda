/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Route, Redirect} from 'react-router-dom';
import {addHandler, removeHandler} from 'request';
import {addNotification} from 'notifications';
import debounce from 'debounce';

export default class PrivateRoute extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      forceRedirect: false
    };
  }

  componentDidMount() {
    addHandler(this.handleResponse);
  }

  handleResponse = response => {
    if (response.status === 401) {
      this.setState({
        forceRedirect: true
      });
    }

    return response;
  };

  render() {
    const {component: Component, ...rest} = this.props;
    return (
      <Route
        {...rest}
        render={props => {
          return !this.state.forceRedirect ? (
            <Component {...props} />
          ) : (
            <Redirect
              to={{
                pathname: '/login',
                state: {from: props.location}
              }}
            />
          );
        }}
      />
    );
  }

  componentDidUpdate() {
    if (this.state.forceRedirect) {
      this.setState({
        forceRedirect: false
      });
    }
  }

  componentWillUnmount() {
    removeHandler(this.handleResponse);
  }
}

// We sometimes trigger multiple requests that all return 401 when the session times out.
// To prevent multiple messages appearing, we debounce the addNotification function by 500ms
const debouncedNotification = debounce((...args) => addNotification(...args), 500, true);

addHandler(response => {
  if (response.status === 401 && !response.url.includes('authentication')) {
    debouncedNotification({text: 'Your Session timed out.', type: 'warning'});
  }

  return response;
});
