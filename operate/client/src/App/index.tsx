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
import {useEffect} from 'react';
import {tracking} from 'modules/tracking';
import {currentTheme} from 'modules/stores/currentTheme';

import {ThemeSwitcher} from 'modules/components/ThemeSwitcher';
import {ForbiddenPage} from 'modules/components/ForbiddenPage';
import {ReactQueryProvider} from 'modules/react-query/ReactQueryProvider';

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
          const {Dashboard} = await import('./Dashboard/index');
          return {Component: Dashboard};
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
    </Route>
  </Route>,
);

const router = createBrowserRouter(routes, {
  basename: import.meta.env.DEV ? '/' : (window.clientConfig?.baseName ?? '/'),
});

const App: React.FC = () => {
  useEffect(() => {
    tracking.track({
      eventName: 'operate-loaded',
      theme: currentTheme.state.selectedTheme,
    });
  }, []);

  return (
    <ErrorBoundary FallbackComponent={ForbiddenPage}>
      <ThemeProvider>
        <ReactQueryProvider>
          <ThemeSwitcher />
          <Notifications />
          <NetworkStatusWatcher />
          <RouterProvider router={router} />
        </ReactQueryProvider>
      </ThemeProvider>
    </ErrorBoundary>
  );
};

export {App};
