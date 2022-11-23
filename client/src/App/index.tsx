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
import {Login} from './Login';
import {Dashboard} from './Dashboard';
import {Processes} from './Processes';
import {ProcessInstance} from './ProcessInstance';
import {Decisions} from './Decisions';
import {DecisionInstance} from './DecisionInstance';
import GlobalStyles from './GlobalStyles';
import {NetworkStatusWatcher} from './NetworkStatusWatcher';
import {CommonUiContext} from 'modules/CommonUiContext';
import {Paths} from 'modules/routes';
import {RedirectDeprecatedRoutes} from './RedirectDeprecatedRoutes';
import {AuthenticationCheck} from './AuthenticationCheck';
import {SessionWatcher} from './SessionWatcher';
import {Layout} from './Layout';
import {TrackPagination} from 'modules/tracking/TrackPagination';
import {useEffect} from 'react';
import {tracking} from 'modules/tracking';
import {currentTheme} from 'modules/stores/currentTheme';
import {createBrowserHistory} from 'history';

const App: React.FC = () => {
  useEffect(() => {
    tracking.track({
      eventName: 'operate-loaded',
      theme: currentTheme.theme,
    });
  }, []);

  return (
    <ThemeProvider>
      <NotificationProvider>
        <GlobalStyles />
        <NetworkStatusWatcher />
        <CommonUiContext />
        <HistoryRouter
          history={createBrowserHistory({window})}
          basename={window.clientConfig?.contextPath ?? '/'}
        >
          <RedirectDeprecatedRoutes />
          <SessionWatcher />
          <TrackPagination />
          <Routes>
            <Route path={Paths.login()} element={<Login />} />
            <Route
              path={Paths.dashboard()}
              element={
                <AuthenticationCheck redirectPath={Paths.login()}>
                  <Layout />
                </AuthenticationCheck>
              }
            >
              <Route index element={<Dashboard />} />
              <Route path={Paths.processes()} element={<Processes />} />
              <Route
                path={Paths.processInstance()}
                element={<ProcessInstance />}
              />
              <Route path={Paths.decisions()} element={<Decisions />} />
              <Route
                path={Paths.decisionInstance()}
                element={<DecisionInstance />}
              />
            </Route>
          </Routes>
        </HistoryRouter>
      </NotificationProvider>
    </ThemeProvider>
  );
};

export {App};
