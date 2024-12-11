/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/* istanbul ignore file */

import {Outlet, useLocation} from 'react-router-dom';
import {Header} from './Header';
import {AuthenticationCheck} from 'AuthenticationCheck';
import {OSNotifications} from 'OSNotifications';
import {C3Provider} from 'C3Provider';

const Layout: React.FC = () => {
  const location = useLocation();
  return (
    <C3Provider>
      <AuthenticationCheck redirectPath={`/login?next=${location.pathname}`}>
        <OSNotifications />
        <Header />
        <Outlet />
      </AuthenticationCheck>
    </C3Provider>
  );
};

Layout.displayName = 'Layout';

export {Layout as Component};
