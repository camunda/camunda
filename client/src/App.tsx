/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {BrowserRouter, Route, Routes} from 'react-router-dom';
import {ThemeProvider} from 'styled-components';
import {ApolloProvider} from '@apollo/client';
import {NotificationProvider} from 'modules/notifications';
import {NetworkStatusWatcher} from './NetworkStatusWatcher';
import {AuthenticationCheck} from './AuthenticationCheck';
import {Layout} from './Layout';
import {Login} from './Login';
import {Pages} from 'modules/constants/pages';
import {theme} from 'modules/theme';
import {GlobalStyle} from './GlobalStyle';
import {client} from './modules/apollo-client';
import {SessionWatcher} from './SessionWatcher';
import {EmptyDetails} from './EmptyDetails';
import {Task} from './Task';

const App: React.FC = () => {
  return (
    <ApolloProvider client={client}>
      <ThemeProvider theme={theme}>
        <NotificationProvider>
          <GlobalStyle />
          <NetworkStatusWatcher />
          <BrowserRouter basename={window.clientConfig?.contextPath ?? '/'}>
            <SessionWatcher />
            <Routes>
              <Route path={Pages.Login} element={<Login />} />
              <Route
                path={Pages.Initial()}
                element={
                  <AuthenticationCheck redirectPath={Pages.Login}>
                    <Layout />
                  </AuthenticationCheck>
                }
              >
                <Route
                  index
                  element={
                    <EmptyDetails>
                      Select a Task to view the details
                    </EmptyDetails>
                  }
                />
                <Route path={Pages.TaskDetails()} element={<Task />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </NotificationProvider>
      </ThemeProvider>
    </ApolloProvider>
  );
};

export {App};
