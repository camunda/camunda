/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useLocation} from 'react-router-dom';
import {Header} from './Header';
import {Footer, Grid} from './styled';
import {Copyright} from 'modules/components/Copyright';
import {Routes} from 'modules/routes';

const Layout: React.FC = ({children}) => {
  const location = useLocation();
  const showFooter = location.pathname !== Routes.instances();

  return (
    <Grid numberOfRows={showFooter ? 3 : 2}>
      <Header />
      {children}
      {showFooter && (
        <Footer
          variant={
            location.pathname === Routes.dashboard() ? 'dashboard' : 'default'
          }
        >
          <Copyright />
        </Footer>
      )}
    </Grid>
  );
};

export {Layout};
