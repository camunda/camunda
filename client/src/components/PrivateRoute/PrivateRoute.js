/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import ReactDOM from 'react-dom';
import {Route, Redirect} from 'react-router-dom';
import {addHandler, removeHandler, request} from 'request';
import {nowPristine} from 'saveGuard';
import {withUser} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';

import {Header, Footer} from '..';

import {Login} from './Login';

import './PrivateRoute.scss';

export class PrivateRoute extends React.Component {
  state = {
    showLogin: false,
    forceGotoHome: false,
  };
  container = React.createRef();
  componentContainer = document.createElement('div');

  componentDidMount() {
    this.appendComponent();
    addHandler(this.handleResponse);
  }

  componentDidUpdate() {
    const {forceGotoHome} = this.state;

    if (forceGotoHome) {
      this.setState({forceGotoHome: false});
    }

    this.appendComponent();
    this.removeComponentOnLogin();
  }

  appendComponent = () => {
    const {showLogin} = this.state;
    const container = this.container.current;
    if (!showLogin && container && !container.contains(this.componentContainer)) {
      container.appendChild(this.componentContainer);

      // Some components might use refs in componentDidUpdate to query the size of their container.
      // Since in the previous update cycle, they were not part of the DOM tree, their size was incorrect (usually 0 or null)
      // Now that they have been appended to the DOM again, we trigger another update so they can get their correct size
      this.forceUpdate();
    }
  };

  removeComponentOnLogin = () => {
    const {showLogin} = this.state;
    const container = this.container.current;
    if (showLogin && container?.contains(this.componentContainer)) {
      container.removeChild(this.componentContainer);
    }
  };

  handleResponse = (response) => {
    if (response.status === 401) {
      this.setState({
        showLogin: true,
      });
    }

    return response;
  };

  handleLoginSuccess = async () => {
    const {user: oldUser, refreshUser} = this.props;
    const newUser = await refreshUser();

    if (oldUser && newUser.id !== oldUser.id) {
      outstandingRequests.length = 0;
      nowPristine();

      this.setState({forceGotoHome: true});
    }

    this.setState({showLogin: false});
    redoOutstandingRequests();
  };

  render() {
    const {component: Component, ...rest} = this.props;
    return (
      <Route
        {...rest}
        render={(props) => {
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

// keep track of whether we were logged in in the current session
let isLoggedIn = false;
const outstandingRequests = [];
addHandler((response, payload) => {
  // login detection logic does not apply to non-restricted ressources
  if (
    ![
      'api/authentication',
      'api/authentication/logout',
      'api/onboarding/whatsnew',
      'api/ui-configuration',
      'api/localization',
      'api/eventBasedProcess/isEnabled',
      'api/share',
    ].some((url) => payload.url.startsWith(url))
  ) {
    if (response.status === 401) {
      if (isLoggedIn) {
        // if we were logged in before, the session timed out
        showError(t('login.timeout'));
        isLoggedIn = false;
      }
      return new Promise((resolve, reject) => {
        outstandingRequests.push({resolve, reject, payload});
      });
    } else {
      // if we get a non 401 response on a restricted ressource, we know
      // that we are logged in in the current session
      isLoggedIn = true;
    }
  }

  if (payload.url === 'api/authentication/logout') {
    isLoggedIn = false;
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

export default withUser(PrivateRoute);
