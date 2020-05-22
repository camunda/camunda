/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import * as React from 'react';
import {BrowserRouter, Switch, Route} from 'react-router-dom';
import {ThemeProvider} from 'styled-components';

import {PrivateRoute} from './PrivateRoute';
import {Tasklist} from './Tasklist';
import {Login} from './Login';
import {Pages} from './modules/constants/pages';
import {theme} from './modules/theme';
import {GlobalStyle} from './GlobalStyle';

const App: React.FC = () => {
  return (
    <ThemeProvider theme={theme}>
      <GlobalStyle />
      <BrowserRouter>
        <Switch>
          <PrivateRoute
            exact
            path={Pages.Initial}
            component={Tasklist}
            redirectPath={Pages.Login}
          />
          <Route path={Pages.Login} component={Login} />
          <Route render={() => <h1>Page not found</h1>} />
        </Switch>
      </BrowserRouter>
    </ThemeProvider>
  );
};

export {App};
