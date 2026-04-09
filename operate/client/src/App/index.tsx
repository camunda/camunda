/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  Navigate,
  Outlet,
  Route,
  RouterProvider,
  createBrowserRouter,
  createRoutesFromElements,
  useLocation,
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
import {getClientConfig} from 'modules/utils/getClientConfig';

import {ThemeSwitcher} from 'modules/components/ThemeSwitcher';
import {ForbiddenPage} from 'modules/components/ForbiddenPage';
import {ReactQueryProvider} from 'modules/react-query/ReactQueryProvider';
import {PageErrorBoundary} from 'modules/components/PageErrorBoundary';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';

const DefaultTabRedirect: React.FC = () => {
  const location = useLocation();
  const {data: processInstance} = useProcessInstance();
  const pathname =
    processInstance?.hasIncident === true ? 'incidents' : 'variables';
  return <Navigate to={{pathname, search: location.search}} replace />;
};

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
  <Route path="/" element={<Wrapper />} ErrorBoundary={PageErrorBoundary}>
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
        path={Paths.processInstance(undefined, true)}
        lazy={async () => {
          const {ProcessInstance} = await import('./ProcessInstance');
          return {Component: ProcessInstance};
        }}
      >
        <Route
          path={Paths.processInstanceVariables({isRelative: true})}
          lazy={async () => {
            const {VariablesTab} =
              await import('./ProcessInstance/BottomPanelTabs/VariablesTab/index');
            return {Component: VariablesTab};
          }}
        />
        <Route
          path={Paths.processInstanceDetails({isRelative: true})}
          lazy={async () => {
            const {DetailsTab} =
              await import('./ProcessInstance/BottomPanelTabs/DetailsTab/index');
            return {Component: DetailsTab};
          }}
        />
        <Route
          path={Paths.processInstanceIncidents({isRelative: true})}
          lazy={async () => {
            const {IncidentsTab} =
              await import('./ProcessInstance/BottomPanelTabs/IncidentsTab/index');
            return {Component: IncidentsTab};
          }}
        />
        <Route
          path={Paths.processInstanceInputMappings({isRelative: true})}
          lazy={async () => {
            const {InputMappingsTab} =
              await import('./ProcessInstance/BottomPanelTabs/InputMappingsTab');
            return {Component: InputMappingsTab};
          }}
        />
        <Route
          path={Paths.processInstanceOutputMappings({isRelative: true})}
          lazy={async () => {
            const {OutputMappingsTab} =
              await import('./ProcessInstance/BottomPanelTabs/OutputMappingsTab');
            return {Component: OutputMappingsTab};
          }}
        />
        <Route
          path={Paths.processInstanceListeners({isRelative: true})}
          lazy={async () => {
            const {ListenersTab} =
              await import('./ProcessInstance/BottomPanelTabs/ListenersTab/index');
            return {Component: ListenersTab};
          }}
        />
        <Route
          path={Paths.processInstanceOperationsLog({isRelative: true})}
          lazy={async () => {
            const {OperationsLogTab} =
              await import('./ProcessInstance/BottomPanelTabs/OperationsLogTab/index');
            return {Component: OperationsLogTab};
          }}
        />
        <Route
          path={Paths.processInstanceHistory({isRelative: true})}
          lazy={async () => {
            const {InstanceHistoryTab} =
              await import('./ProcessInstance/BottomPanelTabs/InstanceHistoryTab/index');
            return {Component: InstanceHistoryTab};
          }}
        />
        <Route
          path={Paths.processInstanceAgentContext({isRelative: true})}
          lazy={async () => {
            const {AgentContextTab} =
              await import('./ProcessInstance/BottomPanelTabs/AgentContextTab/index');
            return {Component: AgentContextTab};
          }}
        />
        <Route path="*" element={<DefaultTabRedirect />} />
      </Route>
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
  basename: import.meta.env.DEV ? '/' : getClientConfig().baseName,
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
