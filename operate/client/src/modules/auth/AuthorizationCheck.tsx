/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Navigate, useLocation} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {isForbidden} from 'modules/auth/isForbidden';

type Props = {
  children: React.ReactNode;
};

const AuthorizationCheck: React.FC<Props> = ({children}) => {
  const location = useLocation();
  const {data: currentUser} = useCurrentUser();

  if (
    isForbidden(currentUser) &&
    !location.pathname.includes(Paths.forbidden())
  ) {
    return (
      <Navigate
        to={{
          pathname: Paths.forbidden(),
        }}
        state={{
          referrer: location,
        }}
        replace={true}
      />
    );
  }

  return children;
};

export {AuthorizationCheck};
