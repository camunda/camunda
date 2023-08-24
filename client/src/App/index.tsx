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
import {Notifications} from 'modules/carbonNotifications';
import {NetworkStatusWatcher} from './NetworkStatusWatcher';
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
      <NetworkStatusWatcher />
      <HistoryRouter
        history={createBrowserHistory({window})}
        basename={window.clientConfig?.contextPath ?? '/'}
      >
        <RedirectDeprecatedRoutes />
        <SessionWatcher />
        <TrackPagination />
        <Routes>
          <Route path={Paths.login()} element={<CarbonLogin />} />
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
    </ThemeProvider>
  );
};

export {App};
