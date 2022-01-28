/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import {Route, Redirect, RouteProps} from 'react-router-dom';
import {observer} from 'mobx-react-lite';
import {getPersistentQueryParams} from 'modules/utils/getPersistentQueryParams';
import {authenticationStore} from 'modules/stores/authentication';

interface Props extends RouteProps {
  redirectPath: string;
}

const AuthenticatedRoute: React.FC<Props> = observer(
  ({redirectPath, children, location, ...routeProps}) => {
    const {
      state: {status},
      authenticate,
    } = authenticationStore;

    useEffect(() => {
      if (['initial', 'logged-in'].includes(status)) {
        authenticate();
      }
    }, [status, authenticate]);

    if (
      [
        'initial',
        'logged-in',
        'fetching-user-information',
        'user-information-fetched',
      ].includes(status)
    ) {
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
  }
);

export {AuthenticatedRoute};
