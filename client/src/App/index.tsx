/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {BrowserRouter, Route, Routes} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {NotificationProvider} from 'modules/notifications';
import {Login} from './Login';
import {Dashboard} from './Dashboard';
import {Instances} from './Instances';
import {Instance} from './Instance';
import {Decisions} from './Decisions';
import {DecisionInstance} from './DecisionInstance';
import GlobalStyles from './GlobalStyles';
import {NetworkStatusWatcher} from './NetworkStatusWatcher';
import {GettingStartedExperience} from './GettingStartedExperience';
import {CommonUiContext} from 'modules/CommonUiContext';
import {Paths} from 'modules/routes';
import {HashRouterMigrator} from './HashRouterMigrator';
import {AuthenticationCheck} from './AuthenticationCheck';
import {SessionWatcher} from './SessionWatcher';
import {Layout} from './Layout';

const App: React.FC = () => {
  return (
    <ThemeProvider>
      <NotificationProvider>
        <GlobalStyles />
        <NetworkStatusWatcher />
        <CommonUiContext />
        <BrowserRouter basename={window.clientConfig?.contextPath ?? '/'}>
          <GettingStartedExperience />
          <HashRouterMigrator />
          <SessionWatcher />
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
              <Route path={Paths.instances()} element={<Instances />} />
              <Route path={Paths.instance()} element={<Instance />} />
              <Route path={Paths.decisions()} element={<Decisions />} />
              <Route
                path={Paths.decisionInstance()}
                element={<DecisionInstance />}
              />
            </Route>
          </Routes>
        </BrowserRouter>
      </NotificationProvider>
    </ThemeProvider>
  );
};

export {App};
