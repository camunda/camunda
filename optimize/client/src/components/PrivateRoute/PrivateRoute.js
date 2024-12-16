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

import {Header} from '..';

import './PrivateRoute.scss';

export function PrivateRoute({component: Component, ...rest}) {
  useEffect(() => {
    const handleResponse = async (response) => {
      if (response.status === 401) {
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
            <main>
              <div className="PrivateRoute">
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
