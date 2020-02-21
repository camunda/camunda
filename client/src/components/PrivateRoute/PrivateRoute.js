/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import ReactDOM from 'react-dom';
import {Route, Redirect} from 'react-router-dom';
import {addHandler, removeHandler, request} from 'request';
import {loadCurrentUser, getCurrentUser} from 'config';
import {nowPristine} from 'saveGuard';

import {Header, Footer} from '..';

import {Login} from './Login';

import './PrivateRoute.scss';

export default class PrivateRoute extends React.Component {
  state = {
    showLogin: false,
    currentUser: null,
    forceGotoHome: false
  };
  container = React.createRef();
  componentContainer = document.createElement('div');

  componentDidMount() {
    addHandler(this.handleResponse);
    this.setCurrentUser();
  }

  setCurrentUser = async () => {
    this.setState({currentUser: await getCurrentUser()});
  };

  handleResponse = response => {
    if (response.status === 401) {
      this.setState({
        showLogin: true
      });
    }

    return response;
  };

  handleLoginSuccess = async () => {
    const {currentUser: oldUser} = this.state;
    const newUser = await loadCurrentUser();

    if (oldUser && newUser.id !== oldUser.id) {
      outstandingRequests.length = 0;
      nowPristine();

      this.setState({forceGotoHome: true});
    }

    this.setState({showLogin: false, currentUser: newUser});
    redoOutstandingRequests();
  };

  componentDidUpdate() {
    const {forceGotoHome, showLogin} = this.state;
    const container = this.container.current;

    if (forceGotoHome) {
      this.setState({forceGotoHome: false});
    }

    if (!showLogin && container && !container.contains(this.componentContainer)) {
      container.appendChild(this.componentContainer);

      // Some components might use refs in componentDidUpdate to query the size of their container.
      // Since in the previous update cycle, they were not part of the DOM tree, their size was incorrect (usually 0 or null)
      // Now that they have been appended to the DOM again, we trigger another update so they can get their correct size
      this.forceUpdate();
    }

    if (showLogin && container?.contains(this.componentContainer)) {
      container.removeChild(this.componentContainer);
    }
  }

  render() {
    const {component: Component, ...rest} = this.props;
    return (
      <Route
        {...rest}
        render={props => {
          const {showLogin, forceGotoHome} = this.state;

          if (forceGotoHome) {
            return <Redirect to="/" />;
          }

          return (
            <>
              {!showLogin && <Header />}
              <main>
                <div className="PrivateRoute" ref={this.container}></div>
                <Detachable container={this.componentContainer}>
                  {this.props.render ? this.props.render(props) : <Component {...props} />}
                </Detachable>
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

function Detachable({container, children}) {
  return ReactDOM.createPortal(children, container);
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
