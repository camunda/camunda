/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Navigate, useLocation} from 'react-router-dom';
import {observer} from 'mobx-react-lite';
import {authenticationStore} from 'modules/stores/authentication';
import {getPersistentQueryParams} from 'modules/utils/getPersistentQueryParams';

interface Props {
  redirectPath: string;
  children: React.ReactNode;
}

const AuthenticationCheck: React.FC<Props> = observer(
  ({redirectPath, children}) => {
    const location = useLocation();
    const {status} = authenticationStore;

    if (
      ['logged-in', 'initial', 'invalid-third-party-session'].includes(status)
    ) {
      return <>{children}</>;
    }

    return (
      <Navigate
        to={{
          pathname: redirectPath,
          search: getPersistentQueryParams(location?.search ?? ''),
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
