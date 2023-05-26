/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {BrowserRouter, Route, Routes} from 'react-router-dom';
import {Notifications} from 'modules/notifications';
import {NetworkStatusWatcher} from './NetworkStatusWatcher';
import {AuthenticationCheck} from './AuthenticationCheck';
import {Layout} from './Layout';
import {Login} from './Login';
import {pages} from 'modules/routing';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {SessionWatcher} from './SessionWatcher';
import {Tasks} from './Tasks';
import {TrackPagination} from 'modules/tracking/TrackPagination';
import {Processes} from 'Processes';
import {ReactQueryProvider} from 'modules/ReactQueryProvider';

import {Suspense, lazy} from 'react';
import {Loading} from '@carbon/react';

const StartProcessFromForm = lazy(() =>
  import('./StartProcessFromForm').then(({StartProcessFromForm}) => ({
    default: StartProcessFromForm,
  })),
);

const App: React.FC = () => {
  return (
    <ThemeProvider>
      <ReactQueryProvider>
        <Notifications />
        <NetworkStatusWatcher />
        <BrowserRouter basename={window.clientConfig?.contextPath ?? '/'}>
          <SessionWatcher />
          <TrackPagination />
          <Routes>
            <Route path={pages.login} element={<Login />} />
            <Route
              path={pages.startProcessFromForm}
              element={
                <Suspense fallback={<Loading withOverlay />}>
                  <StartProcessFromForm />
                </Suspense>
              }
            />
            <Route
              path="*"
              element={
                <AuthenticationCheck redirectPath={pages.login}>
                  <Layout />
                </AuthenticationCheck>
              }
            >
              <Route path="*" element={<Tasks />} />
              <Route path={pages.processes} element={<Processes />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </ReactQueryProvider>
    </ThemeProvider>
  );
};

export {App};
