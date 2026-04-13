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
import {NetworkStatusWatcher} from 'modules/NetworkStatusWatcher';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {SessionWatcher} from 'modules/auth/SessionWatcher';
import {TrackPagination} from 'modules/tracking/TrackPagination';
import {ReactQueryProvider} from 'modules/react-query/ReactQueryProvider';
import {
  ErrorWithinLayout,
  FallbackErrorPage,
} from 'modules/error-handling/errorBoundaries';
import {tracking} from 'modules/tracking';
import {getClientConfig} from 'modules/config/getClientConfig';
import {Forbidden} from 'pages/Forbidden';

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
      <Route path="login" lazy={() => import('pages/Login')} />
      <Route
        path="new/:bpmnProcessId"
        lazy={() => import('./pages/StartProcessFromForm')}
      />
      <Route path="/" lazy={() => import('./pages/Layout')}>
        <Route path="forbidden" element={<Forbidden />} />
        <Route path="processes" ErrorBoundary={ErrorWithinLayout}>
          <Route index lazy={() => import('./pages/ProcessesTab')} />
          <Route
            path=":processDefinitionKey/start"
            lazy={() => import('./pages/ProcessesTab')}
          />
        </Route>
        <Route
          path="/"
          lazy={() => import('./pages/TasksTab')}
          ErrorBoundary={ErrorWithinLayout}
        >
          <Route
            index
            lazy={() => import('./pages/NoTaskSelected')}
            ErrorBoundary={ErrorWithinLayout}
          />
          <Route
            path=":id"
            lazy={() => import('./pages/TaskDetailsLayout')}
            ErrorBoundary={ErrorWithinLayout}
          >
            <Route index lazy={() => import('./pages/TaskDetails')} />
            <Route
              path="process"
              lazy={() => import('./pages/TaskDetailsProcessView')}
            />
            <Route
              path="history"
              lazy={() => import('./pages/TaskDetailsHistoryView')}
            >
              <Route
                path=":auditLogKey"
                lazy={() => import('./pages/HistoryItemDetailsModal')}
              />
            </Route>
          </Route>
        </Route>
      </Route>
    </Route>,
  ),
  {
    basename: import.meta.env.DEV ? '/' : getClientConfig().baseName,
  },
);

const App: React.FC = () => {
  useEffect(() => {
    tracking.track({
      eventName: 'app-loaded',
      osNotificationPermission:
        'Notification' in window ? Notification.permission : 'default',
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
