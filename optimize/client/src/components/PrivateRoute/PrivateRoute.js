/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useEffect} from 'react';
import {Route} from 'react-router-dom';
import {addHandler, removeHandler} from 'request';
import {storePostLoginRedirect} from 'postLoginRedirect';
import {IS_NAV_V2_ENABLED} from 'feature-flags';

import {Header} from '..';

import './PrivateRoute.scss';

export function PrivateRoute({component: Component, ...rest}) {
  useEffect(() => {
    const handleResponse = async (response) => {
      if (response.status === 401) {
        // stash the current route so it can be restored after the login round trip (ADR-0038:
        // https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0038-optimize-reuses-stateful-oidc-webapp-chain.md)
        storePostLoginRedirect();
        // reload to reinitialize the login flow on timeout
        window.location.reload();
      }

      return response;
    };
    addHandler(handleResponse);

    return () => {
      removeHandler(handleResponse);
    };
  }, []);

  return (
    <Route
      {...rest}
      render={(props) => {
        return (
          <>
            <Header />
            <main className={IS_NAV_V2_ENABLED ? 'nav-v2' : undefined}>
              <div
                className={'PrivateRoute' + (IS_NAV_V2_ENABLED ? ' nav-v2' : '')}
                id="main-content"
                tabIndex={-1}
              >
                {rest.render ? rest.render(props) : <Component {...props} />}
              </div>
            </main>
          </>
        );
      }}
    />
  );
}

export default PrivateRoute;
