/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Route} from 'react-router-dom';
import classnames from 'classnames';
import {addHandler, removeHandler, request} from 'request';

import {Login} from './Login';

import './PrivateRoute.scss';

export default class PrivateRoute extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      showLogin: false
    };
  }

  componentDidMount() {
    addHandler(this.handleResponse);
  }

  handleResponse = (response, payload) => {
    if (response.status === 401) {
      this.setState({
        showLogin: true
      });
    } else if (response.status === 200 && payload.url === 'api/authentication') {
      this.setState({
        showLogin: false
      });
    }

    return response;
  };

  handleLoginSuccess = () => {
    this.setState({showLogin: false});
    redoOutstandingRequests();
  };

  render() {
    const {component: Component, ...rest} = this.props;
    return (
      <Route
        {...rest}
        render={props => {
          return (
            <>
              <div className={classnames('PrivateRoute', {showLogin: this.state.showLogin})}>
                {this.props.render ? this.props.render(props) : <Component {...props} />}
              </div>
              {this.state.showLogin && <Login {...props} onLogin={this.handleLoginSuccess} />}
            </>
          );
        }}
      />
    );
  }

  componentWillUnmount() {
    removeHandler(this.handleResponse);
  }
}

const outstandingRequests = [];
addHandler((response, payload) => {
  if (response.status === 401 && payload.url !== 'api/authentication') {
    return new Promise((resolve, reject) => {
      outstandingRequests.push({resolve, reject, payload});
    });
  }

  return response;
}, -1);

function redoOutstandingRequests() {
  outstandingRequests.forEach(async ({resolve, reject, payload}) => {
    try {
      resolve(await request(payload));
    } catch (e) {
      reject(e);
    }
  });
  outstandingRequests.length = 0;
}
