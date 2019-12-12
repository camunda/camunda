/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Route} from 'react-router-dom';
import classnames from 'classnames';
import {addHandler, removeHandler, request} from 'request';

import {Header, Footer} from '..';

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
          const {showLogin} = this.state;
          return (
            <>
              {!showLogin && <Header name="Camunda Optimize" />}
              <main>
                <div className={classnames('PrivateRoute', {showLogin})}>
                  {this.props.render ? this.props.render(props) : <Component {...props} />}
                </div>
                {showLogin && <Login {...props} onLogin={this.handleLoginSuccess} />}
              </main>
              {!showLogin && <Footer />}
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
  if (
    response.status === 401 &&
    !['api/authentication', 'api/onboarding/whatsnew', 'api/eventBasedProcess/isEnabled'].includes(
      payload.url
    )
  ) {
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
