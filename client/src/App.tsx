/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {
  Outlet,
  Route,
  RouterProvider,
  createBrowserRouter,
  createRoutesFromElements,
} from 'react-router-dom';
import {Notifications} from 'modules/notifications';
import {NetworkStatusWatcher} from './NetworkStatusWatcher';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {SessionWatcher} from './SessionWatcher';
import {TrackPagination} from 'modules/tracking/TrackPagination';
import {ReactQueryProvider} from 'modules/react-query/ReactQueryProvider';

const Wrapper: React.FC = () => {
  return (
    <>
      <SessionWatcher />
      <TrackPagination />
      <Outlet />
    </>
  );
};

const router = createBrowserRouter(
  createRoutesFromElements(
    <Route path="/" element={<Wrapper />}>
      <Route path="login" lazy={() => import('./Login')} />
      <Route
        path="new/:bpmnProcessId"
        lazy={() => import('./StartProcessFromForm')}
      />
      <Route path="/" lazy={() => import('./Layout')}>
        <Route path="processes">
          <Route index lazy={() => import('./Processes')} />
          <Route
            path=":bpmnProcessId/start"
            lazy={() => import('./Processes')}
          />
        </Route>
        <Route path="/" lazy={() => import('./Tasks')}>
          <Route index lazy={() => import('./Tasks/EmptyPage')} />
          <Route path=":id" lazy={() => import('./Tasks/Task')} />
        </Route>
      </Route>
    </Route>,
  ),
  {
    basename: window.clientConfig?.contextPath ?? '/',
  },
);

const App: React.FC = () => {
  return (
    <ThemeProvider>
      <ReactQueryProvider>
        <Notifications />
        <NetworkStatusWatcher />
        <RouterProvider router={router} />
      </ReactQueryProvider>
    </ThemeProvider>
  );
};

export {App};
