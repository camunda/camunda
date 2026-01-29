/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Outlet,
  Route,
  RouterProvider,
  createBrowserRouter,
  createRoutesFromElements,
} from 'react-router-dom';
import {ErrorBoundary} from 'react-error-boundary';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Notifications} from 'modules/notifications';
import {NetworkStatusWatcher} from './NetworkStatusWatcher';
import {Paths} from 'modules/Routes';
import {RedirectDeprecatedRoutes} from './RedirectDeprecatedRoutes';
import {AuthenticationCheck} from '../modules/auth/AuthenticationCheck';
import {AuthorizationCheck} from '../modules/auth/AuthorizationCheck';
import {SessionWatcher} from '../modules/auth/SessionWatcher';
import {TrackPagination} from 'modules/tracking/TrackPagination';
import {useEffect, useState} from 'react';
import {tracking} from 'modules/tracking';
import {currentTheme} from 'modules/stores/currentTheme';

import {ThemeSwitcher} from 'modules/components/ThemeSwitcher';
import {ForbiddenPage} from 'modules/components/ForbiddenPage';
import {ReactQueryProvider} from 'modules/react-query/ReactQueryProvider';
import {getClientConfig} from '../modules/utils/getClientConfig';
import {WebSocketContext} from 'modules/websocket/WebSocketProvider';

const Wrapper: React.FC = () => {
  return (
    <>
      <RedirectDeprecatedRoutes />
      <SessionWatcher />
      <TrackPagination />
      <Outlet />
    </>
  );
};

const routes = createRoutesFromElements(
  <Route path="/" element={<Wrapper />}>
    <Route
      path={Paths.login()}
      lazy={async () => {
        const {Login} = await import('./Login/index');
        return {Component: Login};
      }}
    />
    <Route path={Paths.forbidden()} element={<ForbiddenPage />} />
    <Route
      path={Paths.dashboard()}
      lazy={async () => {
        const {Layout} = await import('./Layout/index');
        return {
          Component: () => (
            <AuthenticationCheck redirectPath={Paths.login()}>
              <AuthorizationCheck>
                <Layout />
              </AuthorizationCheck>
            </AuthenticationCheck>
          ),
        };
      }}
    >
      <Route
        index
        lazy={async () => {
          if (getClientConfig()?.databaseType === 'rdbms') {
            const {Dashboard} = await import('./Dashboard/v2/index');
            return {Component: Dashboard};
          } else {
            const {Dashboard} = await import('./Dashboard/index');
            return {Component: Dashboard};
          }
        }}
      />
      <Route
        path={Paths.processes()}
        lazy={async () => {
          const {Processes} = await import('./Processes/index');
          return {Component: Processes};
        }}
      />
      <Route
        path={Paths.processInstance()}
        lazy={async () => {
          const {ProcessInstance} = await import('./ProcessInstance');
          return {Component: ProcessInstance};
        }}
      />
      <Route
        path={Paths.decisions()}
        lazy={async () => {
          const {Decisions} = await import('./Decisions/index');
          return {Component: Decisions};
        }}
      />
      <Route
        path={Paths.decisionInstance()}
        lazy={async () => {
          const {DecisionInstance} = await import('./DecisionInstance/index');
          return {Component: DecisionInstance};
        }}
      />
      <Route
        path={Paths.batchOperations()}
        lazy={async () => {
          const {BatchOperations} = await import('./BatchOperations/index');
          return {Component: BatchOperations};
        }}
      />
      <Route
        path={Paths.batchOperation()}
        lazy={async () => {
          const {BatchOperation} = await import('./BatchOperation/index');
          return {Component: BatchOperation};
        }}
      />
      <Route
        path={Paths.operationsLog()}
        lazy={async () => {
          const {OperationsLog} = await import('./OperationsLog/index');
          return {Component: OperationsLog};
        }}
      />
    </Route>
  </Route>,
);

const router = createBrowserRouter(routes, {
  basename: import.meta.env.DEV ? '/' : (window.clientConfig?.baseName ?? '/'),
});

const App: React.FC = () => {
  const [websocket, setWebsocket] = useState<WebSocket | null>(null);

  useEffect(() => {
    tracking.track({
      eventName: 'operate-loaded',
      theme: currentTheme.state.selectedTheme,
    });

    const websocket = new WebSocket('ws://localhost:8080/v2/ws');
    setWebsocket(websocket);
    websocket.onopen = () => {
      console.log('connected');
    };
    return () => {
      websocket.close();
    };
  }, []);

  return (
    <ErrorBoundary FallbackComponent={ForbiddenPage}>
      <ThemeProvider>
        <ReactQueryProvider>
          <ThemeSwitcher />
          <Notifications />
          <NetworkStatusWatcher />
          <WebSocketContext.Provider value={websocket}>
            <RouterProvider router={router} />
          </WebSocketContext.Provider>
        </ReactQueryProvider>
      </ThemeProvider>
    </ErrorBoundary>
  );
};

export {App};
