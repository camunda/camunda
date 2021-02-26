/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import * as React from 'react';
import {Route, Redirect, RouteProps} from 'react-router-dom';
import {observer} from 'mobx-react-lite';

import {login} from 'modules/stores/login';
import {getPersistentQueryParams} from 'modules/utils/getPersistentQueryParams';

interface Props extends RouteProps {
  redirectPath: string;
}

const PrivateRoute: React.FC<Props> = observer(
  ({redirectPath, children, location, ...routeProps}) => {
    const {status} = login;

    if (['logged-in', 'initial'].includes(status)) {
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
          state: {
            referrer: location,
          },
          search: getPersistentQueryParams(location?.search ?? ''),
        }}
      />
    );
  },
);

export {PrivateRoute};
