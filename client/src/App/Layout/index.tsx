/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Outlet, useMatch} from 'react-router-dom';
import {Header} from './Header';
import {Footer, Grid} from './styled';
import {Copyright} from 'modules/components/Copyright';
import {Paths} from 'modules/routes';

const Layout: React.FC = () => {
  const instancesMatch = useMatch(Paths.instances());
  const dashboardMatch = useMatch(Paths.dashboard());
  const showFooter = instancesMatch === null;

  return (
    <Grid numberOfRows={showFooter ? 3 : 2}>
      <Header />
      <Outlet />
      {showFooter && (
        <Footer variant={dashboardMatch !== null ? 'dashboard' : 'default'}>
          <Copyright />
        </Footer>
      )}
    </Grid>
  );
};

export {Layout};
