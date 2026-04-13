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
import {Notifications} from 'v2/notifications';
import {NetworkStatusWatcher} from 'v2/NetworkStatusWatcher';
import {ThemeProvider} from 'v2/theme/ThemeProvider';
import {SessionWatcher} from 'v2/auth/SessionWatcher';
import {TrackPagination} from 'v2/tracking/TrackPagination';
import {ReactQueryProvider} from 'v2/react-query/ReactQueryProvider';
import {
  ErrorWithinLayout,
  FallbackErrorPage,
} from 'v2/error-handling/errorBoundaries';
import {tracking} from 'v2/tracking';
import {getClientConfig} from 'v2/config/getClientConfig';
import {Forbidden} from 'v2/error-handling/Forbidden';

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
      <Route path="login" lazy={() => import('v2/auth/Login')} />
      <Route
        path="new/:bpmnProcessId"
        lazy={() => import('./v2/StartProcessFromForm')}
      />
      <Route path="/" lazy={() => import('./v2/components/Layout')}>
        <Route path="forbidden" element={<Forbidden />} />
        <Route path="processes" ErrorBoundary={ErrorWithinLayout}>
          <Route index lazy={() => import('./v2/ProcessesTab')} />
          <Route
            path=":processDefinitionKey/start"
            lazy={() => import('./v2/ProcessesTab')}
          />
        </Route>
        <Route
          path="/"
          lazy={() => import('./v2/TasksTab')}
          ErrorBoundary={ErrorWithinLayout}
        >
          <Route
            index
            lazy={() => import('./v2/tasks/EmptyPage')}
            ErrorBoundary={ErrorWithinLayout}
          />
          <Route
            path=":id"
            lazy={() => import('./v2/TaskDetailsLayout')}
            ErrorBoundary={ErrorWithinLayout}
          >
            <Route index lazy={() => import('./v2/TaskDetails')} />
            <Route
              path="process"
              lazy={() => import('./v2/TaskDetailsProcessView')}
            />
            <Route
              path="history"
              lazy={() => import('./v2/TaskDetailsHistoryView')}
            >
              <Route
                path=":auditLogKey"
                lazy={() =>
                  import('./v2/TaskDetailsHistoryView/HistoryItemDetailsModal')
                }
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
