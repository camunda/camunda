/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {Navigate, useLocation} from 'react-router-dom';
import {observer} from 'mobx-react-lite';
import {authenticationStore} from 'modules/stores/authentication';

type Props = {
  redirectPath: string;
  children: React.ReactNode;
};

const AuthenticationCheck: React.FC<Props> = observer(
  ({redirectPath, children}) => {
    const location = useLocation();
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
        'invalid-third-party-session',
      ].includes(status)
    ) {
      return <>{children}</>;
    }

    return (
      <Navigate
        to={{
          pathname: redirectPath,
        }}
        state={{
          referrer: location,
        }}
        replace={true}
      />
    );
  },
);

export {AuthenticationCheck};
