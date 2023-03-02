/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useMemo, useRef, useState} from 'react';
import ReactDOM from 'react-dom';
import {Route, Redirect} from 'react-router-dom';
import {addHandler, removeHandler} from 'request';
import {nowPristine} from 'saveGuard';
import {withUser} from 'HOC';
import {showError} from 'notifications';
import {t} from 'translation';

import {Header, Footer} from '..';

import {
  createOutstandingRequestPromise,
  redoOutstandingRequests,
  resetOutstandingRequests,
} from './outstandingRequestsService';
import {Login} from './Login';

import './PrivateRoute.scss';

export function PrivateRoute({user: oldUser, refreshUser, component: Component, ...rest}) {
  const [showLogin, setShowLogin] = useState(false);
  const [forceGoToHome, setForceGoToHome] = useState(false);
  const container = useRef();
  const componentContainer = useRef();
  const isLoggedIn = useRef(false);

  useMemo(() => {
    componentContainer.current = document.createElement('div');
  }, []);

  useEffect(() => {
    const handleResponse = async (response, payload) => {
      if (response.status === 401) {
        if (isLoggedIn.current) {
          showError(t('login.timeout'));
        }
        setShowLogin(true);
      }

      isLoggedIn.current = getNewLoginState(isLoggedIn.current, response, payload);
      return response;
    };
    addHandler(handleResponse);

    return () => {
      removeHandler(handleResponse);
    };
  }, []);

  useEffect(() => {
    if (
      !showLogin &&
      container.current &&
      !container.current.contains(componentContainer.current)
    ) {
      container.current.appendChild(componentContainer.current);
    }

    if (showLogin && container.current?.contains(componentContainer.current)) {
      container.current.removeChild(componentContainer.current);
    }
  }, [showLogin]);

  useEffect(() => {
    if (forceGoToHome) {
      setForceGoToHome(false);
    }
  }, [forceGoToHome]);

  const handleLoginSuccess = async () => {
    const newUser = await refreshUser();

    if (oldUser && newUser.id !== oldUser.id) {
      resetOutstandingRequests();
      nowPristine();

      setForceGoToHome(true);
    }

    setShowLogin(false);
    redoOutstandingRequests();
  };

  return (
    <Route
      {...rest}
      render={(props) => {
        if (forceGoToHome) {
          return <Redirect to="/" />;
        }

        return (
          <>
            {!showLogin && <Header />}
            <main>
              <div className="PrivateRoute" ref={container}></div>
              <Detachable container={componentContainer.current}>
                {rest.render ? rest.render(props) : <Component {...props} />}
              </Detachable>
              {showLogin && <Login {...props} onLogin={handleLoginSuccess} />}
            </main>
            {!showLogin && <Footer />}
          </>
        );
      }}
    />
  );
}

export default withUser(PrivateRoute);

function Detachable({container, children}) {
  return ReactDOM.createPortal(children, container);
}

// keep track of whether we were logged in in the current session
addHandler((response, payload) => {
  if (response.status === 401 && isPrivateResource(payload)) {
    return createOutstandingRequestPromise(payload);
  }

  return response;
}, -1);

function getNewLoginState(currentLoginState, response, payload) {
  if (response.status === 401 || payload.url === 'api/authentication/logout') {
    return false;
  }
  if (isPrivateResource(payload)) {
    return true;
  }

  return currentLoginState;
}

function isPrivateResource(payload) {
  return !['api/authentication', 'api/ui-configuration', 'api/localization', 'api/share'].some(
    (url) => payload.url.startsWith(url)
  );
}
