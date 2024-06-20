/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/* istanbul ignore file */

import {useEffect} from 'react';
import {
  Outlet,
  Route,
  RouterProvider,
  createBrowserRouter,
  createRoutesFromElements,
} from 'react-router-dom';
import {ErrorBoundary} from 'react-error-boundary';
import {Notifications} from 'modules/notifications';
import {NetworkStatusWatcher} from './NetworkStatusWatcher';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {SessionWatcher} from './SessionWatcher';
import {TrackPagination} from 'modules/tracking/TrackPagination';
import {ReactQueryProvider} from 'modules/react-query/ReactQueryProvider';
import {ErrorWithinLayout, FallbackErrorPage} from 'errorBoundaries';
import {tracking} from 'modules/tracking';

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
    <Route path="/" element={<Wrapper />} ErrorBoundary={ErrorWithinLayout}>
      <Route path="login" lazy={() => import('./Login')} />
      <Route
        path="new/:bpmnProcessId"
        lazy={() => import('./StartProcessFromForm')}
      />
      <Route path="/" lazy={() => import('./Layout')}>
        <Route path="processes" ErrorBoundary={ErrorWithinLayout}>
          <Route index lazy={() => import('./Processes')} />
          <Route
            path=":bpmnProcessId/start"
            lazy={() => import('./Processes')}
          />
        </Route>
        <Route
          path="/"
          lazy={() => import('./Tasks')}
          ErrorBoundary={ErrorWithinLayout}
        >
          <Route
            index
            lazy={() => import('./Tasks/EmptyPage')}
            ErrorBoundary={ErrorWithinLayout}
          />
          <Route
            path=":id"
            lazy={() => import('./Tasks/Task/Details')}
            ErrorBoundary={ErrorWithinLayout}
          >
            <Route index lazy={() => import('./Tasks/Task')} />
            <Route
              path="process"
              lazy={() => import('./Tasks/Task/ProcessView')}
            />
          </Route>
        </Route>
      </Route>
    </Route>,
  ),
  {
    basename: import.meta.env.DEV ? '/' : window.clientConfig?.baseName ?? '/',
  },
);

const App: React.FC = () => {
  useEffect(() => {
    tracking.track({
      eventName: 'app-loaded',
      osNotificationPermission: Notification.permission,
    });
  }, []);

  return (
    <ErrorBoundary FallbackComponent={FallbackErrorPage}>
      <ThemeProvider>
        <ReactQueryProvider>
          <Notifications />
          <NetworkStatusWatcher />
          <RouterProvider router={router} />
        </ReactQueryProvider>
      </ThemeProvider>
    </ErrorBoundary>
  );
};

export {App};
