/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {Outlet} from 'react-router-dom';
import {Header} from './Header';
import {AuthenticationCheck} from 'AuthenticationCheck';
import {pages} from 'modules/routing';

const Layout: React.FC = () => {
  return (
    <AuthenticationCheck redirectPath={pages.login}>
      <Header />
      <Outlet />
    </AuthenticationCheck>
  );
};

Layout.displayName = 'Layout';

export {Layout as Component};
