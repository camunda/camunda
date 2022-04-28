/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect} from 'react';
import {Navigate, useLocation} from 'react-router-dom';
import {observer} from 'mobx-react-lite';
import {authenticationStore} from 'modules/stores/authentication';

interface Props {
  redirectPath: string;
  children: React.ReactNode;
}

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
  }
);

export {AuthenticationCheck};
