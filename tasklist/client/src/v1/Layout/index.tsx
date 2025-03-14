/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/* istanbul ignore file */

import {Outlet} from 'react-router-dom';
import {Header} from './Header';
import {AuthenticationCheck} from 'common/auth/AuthenticationCheck';
import {pages} from 'common/routing';
import {OSNotifications} from 'v1/OSNotifications';
import {C3Provider} from 'v1/C3Provider';

const Layout: React.FC = () => {
  return (
    <C3Provider>
      <AuthenticationCheck redirectPath={pages.login}>
        <OSNotifications />
        <Header />
        <Outlet />
      </AuthenticationCheck>
    </C3Provider>
  );
};

Layout.displayName = 'Layout';

export {Layout as Component};
