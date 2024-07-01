/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect} from 'react';
import {Route} from 'react-router-dom';
import {addHandler, removeHandler} from 'request';

import {Header, Footer} from '..';

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
            <Footer />
          </>
        );
      }}
    />
  );
}

export default PrivateRoute;
