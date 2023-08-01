/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  unstable_HistoryRouter as HistoryRouter,
  Route,
  Routes,
} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {NotificationProvider} from 'modules/notifications';
import {Notifications} from 'modules/carbonNotifications';
import GlobalStyles from './GlobalStyles';
import {NetworkStatusWatcher} from './NetworkStatusWatcher';
import {LegacyNetworkStatusWatcher} from './LegacyNetworkStatusWatcher';
import {CommonUiContext} from 'modules/CommonUiContext';
import {LegacyPaths} from 'modules/legacyRoutes';
import {Paths} from 'modules/Routes';
import {RedirectDeprecatedRoutes} from './RedirectDeprecatedRoutes';
import {AuthenticationCheck} from './AuthenticationCheck';
import {SessionWatcher} from './SessionWatcher';
import {TrackPagination} from 'modules/tracking/TrackPagination';
import {useEffect} from 'react';
import {tracking} from 'modules/tracking';
import {currentTheme} from 'modules/stores/currentTheme';
import {createBrowserHistory} from 'history';
import {ThemeSwitcher} from 'modules/components/ThemeSwitcher';
import loadable from '@loadable/component';

const Login = loadable(() => import('./Login/index'), {
  resolveComponent: (components) => components.Login,
});

const Layout = loadable(() => import('./Layout/index'), {
  resolveComponent: (components) => components.Layout,
});

const Dashboard = loadable(() => import('./Dashboard/index'), {
  resolveComponent: (components) => components.Dashboard,
});

const Decisions = loadable(() => import('./Decisions/index'), {
  resolveComponent: (components) => components.Decisions,
});

const Processes = loadable(() => import('./Processes/index'), {
  resolveComponent: (components) => components.Processes,
});

const ProcessInstance = loadable(() => import('./ProcessInstance/index'), {
  resolveComponent: (components) => components.ProcessInstance,
});

const DecisionInstance = loadable(() => import('./DecisionInstance/index'), {
  resolveComponent: (components) => components.DecisionInstance,
});

const CarbonLogin = loadable(() => import('./Carbon/Login/index'), {
  resolveComponent: (components) => components.Login,
});

const CarbonLayout = loadable(() => import('./Carbon/Layout/index'), {
  resolveComponent: (components) => components.Layout,
});

const CarbonDashboard = loadable(() => import('./Carbon/Dashboard/index'), {
  resolveComponent: (components) => components.Dashboard,
});

const CarbonDecisions = loadable(() => import('./Carbon/Decisions/index'), {
  resolveComponent: (components) => components.Decisions,
});

const CarbonProcesses = loadable(() => import('./Carbon/Processes/index'), {
  resolveComponent: (components) => components.Processes,
});

const CarbonProcessInstance = loadable(
  () => import('./Carbon/ProcessInstance/index'),
  {
    resolveComponent: (components) => components.ProcessInstance,
  },
);

const CarbonDecisionInstance = loadable(
  () => import('./Carbon/DecisionInstance/index'),
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
      <ThemeSwitcher />
      <Notifications />
      <NotificationProvider>
        {window.location.pathname.includes('legacy') ? (
          <LegacyNetworkStatusWatcher />
        ) : (
          <NetworkStatusWatcher />
        )}
        <CommonUiContext />
        <HistoryRouter
          history={createBrowserHistory({window})}
          basename={window.clientConfig?.contextPath ?? '/'}
        >
          <RedirectDeprecatedRoutes />
          <SessionWatcher />
          <TrackPagination />
          <Routes>
            <Route
              path={LegacyPaths.login()}
              element={
                <>
                  <GlobalStyles />
                  <Login />
                </>
              }
            />
            <Route path={Paths.login()} element={<CarbonLogin />} />
            <Route
              path={LegacyPaths.dashboard()}
              element={
                <AuthenticationCheck redirectPath={LegacyPaths.login()}>
                  <GlobalStyles />
                  <Layout />
                </AuthenticationCheck>
              }
            >
              <Route index element={<Dashboard />} />
              <Route path={LegacyPaths.processes()} element={<Processes />} />
              <Route
                path={LegacyPaths.processInstance()}
                element={<ProcessInstance />}
              />
              <Route path={LegacyPaths.decisions()} element={<Decisions />} />
              <Route
                path={LegacyPaths.decisionInstance()}
                element={<DecisionInstance />}
              />
            </Route>
            <Route
              path={Paths.dashboard()}
              element={
                <AuthenticationCheck redirectPath={Paths.login()}>
                  <CarbonLayout />
                </AuthenticationCheck>
              }
            >
              <Route index element={<CarbonDashboard />} />
              <Route path={Paths.processes()} element={<CarbonProcesses />} />
              <Route
                path={Paths.processInstance()}
                element={<CarbonProcessInstance />}
              />
              <Route path={Paths.decisions()} element={<CarbonDecisions />} />
              <Route
                path={Paths.decisionInstance()}
                element={<CarbonDecisionInstance />}
              />
            </Route>
          </Routes>
        </HistoryRouter>
      </NotificationProvider>
    </ThemeProvider>
  );
};

export {App};
