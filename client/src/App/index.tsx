/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {BrowserRouter, Route, Switch} from 'react-router-dom';
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
import {Routes} from 'modules/routes';
import {HashRouterMigrator} from './HashRouterMigrator';
import {AuthenticatedRoute} from './AuthenticatedRoute';
import {Layout} from './Layout';
import {SessionWatcher} from './SessionWatcher';

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
          <Switch>
            <Route path={Routes.login()}>
              <Login />
            </Route>
            <AuthenticatedRoute
              exact
              path={Routes.dashboard()}
              redirectPath={Routes.login()}
            >
              <Layout>
                <Dashboard />
              </Layout>
            </AuthenticatedRoute>
            <AuthenticatedRoute
              exact
              path={Routes.instances()}
              redirectPath={Routes.login()}
            >
              <Layout>
                <Instances />
              </Layout>
            </AuthenticatedRoute>
            <AuthenticatedRoute
              exact
              path={Routes.instance()}
              redirectPath={Routes.login()}
            >
              <Layout>
                <Instance />
              </Layout>
            </AuthenticatedRoute>
            <AuthenticatedRoute
              exact
              path={Routes.decisions()}
              redirectPath={Routes.login()}
            >
              <Layout>
                <Decisions />
              </Layout>
            </AuthenticatedRoute>
            <AuthenticatedRoute
              exact
              path={Routes.decisionInstance()}
              redirectPath={Routes.login()}
            >
              <Layout>
                <DecisionInstance />
              </Layout>
            </AuthenticatedRoute>
          </Switch>
        </BrowserRouter>
      </NotificationProvider>
    </ThemeProvider>
  );
};

export {App};
