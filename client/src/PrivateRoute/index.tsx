/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {Route, Redirect, RouteProps} from 'react-router-dom';
import {observer} from 'mobx-react-lite';

import {login} from 'modules/stores/login';

interface Props extends RouteProps {
  redirectPath: string;
}

const PrivateRoute: React.FC<Props> = observer(
  ({redirectPath, children, location, path, ...routeProps}) => {
    const {isLoggedIn, isCheckingExistingSession} = login;

    if (isLoggedIn || isCheckingExistingSession) {
      return (
        <Route location={location} {...routeProps}>
          {children}
        </Route>
      );
    }

    return (
      <Redirect
        to={{
          pathname: redirectPath,
          state: {referrer: location?.pathname},
        }}
      />
    );
  },
);

export {PrivateRoute};
