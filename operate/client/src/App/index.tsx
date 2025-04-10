/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  unstable_HistoryRouter as HistoryRouter,
  Route,
  Routes,
} from 'react-router-dom';
import loadable from '@loadable/component';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Notifications} from 'modules/notifications';
import {NetworkStatusWatcher} from './NetworkStatusWatcher';
import {Paths} from 'modules/Routes';
import {RedirectDeprecatedRoutes} from './RedirectDeprecatedRoutes';
import {AuthenticationCheck} from './AuthenticationCheck';
import {AuthorizationCheck} from './AuthorizationCheck';
import {SessionWatcher} from './SessionWatcher';
import {TrackPagination} from 'modules/tracking/TrackPagination';
import {useEffect} from 'react';
import {tracking} from 'modules/tracking';
import {currentTheme} from 'modules/stores/currentTheme';
import {createBrowserHistory} from 'history';
import {ThemeSwitcher} from 'modules/components/ThemeSwitcher';
import {ForbiddenPage} from 'modules/components/ForbiddenPage';
import {ReactQueryProvider} from 'modules/react-query/ReactQueryProvider';
import {IS_FLOWNODE_INSTANCE_STATISTICS_V2_ENABLED} from 'modules/feature-flags';

const CarbonLogin = loadable(() => import('./Login/index'), {
  resolveComponent: (components) => components.Login,
});

const CarbonLayout = loadable(() => import('./Layout/index'), {
  resolveComponent: (components) => components.Layout,
});

const CarbonDashboard = loadable(() => import('./Dashboard/index'), {
  resolveComponent: (components) => components.Dashboard,
});

const CarbonDecisions = loadable(() => import('./Decisions/index'), {
  resolveComponent: (components) => components.Decisions,
});

const CarbonProcesses = loadable(() => import('./Processes/index'), {
  resolveComponent: (components) => components.Processes,
});

const CarbonProcessInstance = loadable(
  () => import('./ProcessInstance/index'),
  {
    resolveComponent: (components) => components.ProcessInstance,
  },
);

const CarbonProcessInstanceV2 = loadable(
  () => import('./ProcessInstance/v2/index'),
  {
    resolveComponent: (components) => components.ProcessInstance,
  },
);

const CarbonDecisionInstance = loadable(
  () => import('./DecisionInstance/index'),
  {
    resolveComponent: (components) => components.DecisionInstance,
  },
);

const App: React.FC = () => {
  useEffect(() => {
    tracking.track({
      eventName: 'operate-loaded',
      theme: currentTheme.state.selectedTheme,
    });
  }, []);

  return (
    <ThemeProvider>
      <ReactQueryProvider>
        <ThemeSwitcher />
        <Notifications />
        <NetworkStatusWatcher />
        <HistoryRouter
          history={createBrowserHistory({window})}
          basename={window.clientConfig?.baseName ?? '/'}
        >
          <RedirectDeprecatedRoutes />
          <SessionWatcher />
          <TrackPagination />
          <Routes>
            <Route path={Paths.login()} element={<CarbonLogin />} />
            <Route path={Paths.forbidden()} element={<ForbiddenPage />} />
            <Route
              path={Paths.dashboard()}
              element={
                <AuthenticationCheck redirectPath={Paths.login()}>
                  <AuthorizationCheck>
                    <CarbonLayout />
                  </AuthorizationCheck>
                </AuthenticationCheck>
              }
            >
              <Route index element={<CarbonDashboard />} />
              <Route path={Paths.processes()} element={<CarbonProcesses />} />
              <Route
                path={Paths.processInstance()}
                element={
                  IS_FLOWNODE_INSTANCE_STATISTICS_V2_ENABLED ? (
                    <CarbonProcessInstanceV2 />
                  ) : (
                    <CarbonProcessInstance />
                  )
                }
              />
              <Route path={Paths.decisions()} element={<CarbonDecisions />} />
              <Route
                path={Paths.decisionInstance()}
                element={<CarbonDecisionInstance />}
              />
            </Route>
          </Routes>
        </HistoryRouter>
      </ReactQueryProvider>
    </ThemeProvider>
  );
};

export {App};
