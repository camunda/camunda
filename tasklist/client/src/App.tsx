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
import {Notifications} from 'common/notifications';
import {NetworkStatusWatcher} from 'common/NetworkStatusWatcher';
import {ThemeProvider} from 'common/theme/ThemeProvider';
import {SessionWatcher} from 'common/auth/SessionWatcher';
import {TrackPagination} from 'common/tracking/TrackPagination';
import {ReactQueryProvider} from 'common/react-query/ReactQueryProvider';
import {
  ErrorWithinLayout,
  FallbackErrorPage,
} from 'common/error-handling/errorBoundaries';
import {tracking} from 'common/tracking';
import {getClientConfig} from 'common/config/getClientConfig';
import {Forbidden} from 'common/error-handling/Forbidden';

const Wrapper: React.FC = () => {
  return (
    <>
      <SessionWatcher />
      <TrackPagination />
      <Outlet />
    </>
  );
};

const v1Routes = createRoutesFromElements(
  <Route path="/" element={<Wrapper />} ErrorBoundary={ErrorWithinLayout}>
    <Route path="login" lazy={() => import('common/auth/Login')} />
    <Route
      path="new/:bpmnProcessId"
      lazy={() => import('./v1/StartProcessFromForm')}
    />
    <Route path="/" lazy={() => import('./common/components/Layout')}>
      <Route path="forbidden" element={<Forbidden />} />
      <Route path="processes" ErrorBoundary={ErrorWithinLayout}>
        <Route index lazy={() => import('./v1/ProcessesTab')} />
        <Route
          path=":bpmnProcessId/start"
          lazy={() => import('./v1/ProcessesTab')}
        />
      </Route>
      <Route
        path="/"
        lazy={() => import('./v1/TasksTab')}
        ErrorBoundary={ErrorWithinLayout}
      >
        <Route
          index
          lazy={() => import('./common/tasks/EmptyPage')}
          ErrorBoundary={ErrorWithinLayout}
        />
        <Route
          path=":id"
          lazy={() => import('./v1/TaskDetailsLayout')}
          ErrorBoundary={ErrorWithinLayout}
        >
          <Route index lazy={() => import('./v1/TaskDetails')} />
          <Route
            path="process"
            lazy={() => import('./v1/TaskDetailsProcessView')}
          />
        </Route>
      </Route>
    </Route>
  </Route>,
);

const v2Routes = createRoutesFromElements(
  <Route path="/" element={<Wrapper />} ErrorBoundary={ErrorWithinLayout}>
    <Route path="login" lazy={() => import('common/auth/Login')} />
    <Route
      path="new/:bpmnProcessId"
      lazy={() => import('./v2/StartProcessFromForm')}
    />
    <Route path="/" lazy={() => import('./common/components/Layout')}>
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
          lazy={() => import('./common/tasks/EmptyPage')}
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
        </Route>
      </Route>
    </Route>
  </Route>,
);

const router = createBrowserRouter(
  getClientConfig().clientMode === 'v1' ? v1Routes : v2Routes,
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
