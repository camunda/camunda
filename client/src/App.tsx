/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {BrowserRouter, Route, Routes} from 'react-router-dom';
import {ApolloProvider} from '@apollo/client';
import {Notifications} from 'modules/notifications';
import {NetworkStatusWatcher} from './NetworkStatusWatcher';
import {AuthenticationCheck} from './AuthenticationCheck';
import {Layout} from './Layout';
import {Login} from './Login';
import {Pages} from 'modules/constants/pages';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {client} from './modules/apollo-client';
import {SessionWatcher} from './SessionWatcher';
import {Tasks} from './Tasks';
import {TrackPagination} from 'modules/tracking/TrackPagination';
import {Processes} from 'Processes';

const App: React.FC = () => {
  return (
    <ThemeProvider>
      <ApolloProvider client={client}>
        <Notifications />
        <NetworkStatusWatcher />
        <BrowserRouter basename={window.clientConfig?.contextPath ?? '/'}>
          <SessionWatcher />
          <TrackPagination />
          <Routes>
            <Route path={Pages.Login} element={<Login />} />
            <Route
              path="*"
              element={
                <AuthenticationCheck redirectPath={Pages.Login}>
                  <Layout />
                </AuthenticationCheck>
              }
            >
              <Route path="*" element={<Tasks />} />
              <Route path={Pages.Processes} element={<Processes />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </ApolloProvider>
    </ThemeProvider>
  );
};

export {App};
