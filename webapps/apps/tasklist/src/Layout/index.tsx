/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/* istanbul ignore file */

import {Outlet} from '@remix-run/react';
import {Header} from './Header';
import {AuthenticationCheck} from 'AuthenticationCheck';
import {pages} from 'modules/routing';
import {OSNotifications} from 'OSNotifications';
import {C3Provider} from 'C3Provider';
// import styles from '@camunda/camunda-composite-components/style.css?url';

// export const links = [{rel: 'stylesheet', href: styles}];

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
