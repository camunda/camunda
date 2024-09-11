/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HashRouter as Router, Route, Switch, matchPath} from 'react-router-dom';
import {Button} from '@carbon/react';

import {
  PrivateRoute,
  Home,
  Collection,
  Report,
  Dashboard,
  Analysis,
  Sharing,
  Logout,
  Processes,
  ProcessReport,
} from './components';

import {ErrorBoundary, ErrorPage} from 'components';

import {Notifications} from 'notifications';
import {SaveGuard} from 'saveGuard';
import {Prompt} from 'prompt';
import {Tracking} from 'tracking';
import {Onboarding} from 'onboarding';
import {ConfigProvider} from 'config';
import {Provider as Theme} from 'theme';
import {UserProvider, DocsProvider} from 'HOC';
import {TranslationProvider} from 'translation';

export default function App({error}) {
  function renderEntity(props) {
    const components = {
      report: Report,
      'dashboard/instant': Dashboard,
      dashboard: Dashboard,
      'processes/report': ProcessReport,
      collection: Collection,
    };
    const entities = ['processes/report', 'report', 'dashboard/instant', 'dashboard', 'collection'];
    let Component, newProps, selectedEntity;
    for (let entity of entities) {
      const splitResult = props.location.pathname.split('/' + entity)[1];
      if (splitResult) {
        const match = matchPath(splitResult, {path: '/:id/:viewMode?'});
        newProps = {
          ...props,
          match,
          entity,
        };
        Component = components[entity];
        selectedEntity = entity;
        break;
      }
    }
    // we do this to get a fresh component whenever the entity type changes
    // this is needed to be able to create new dashboard from instant preview dashboard page
    return <Component key={selectedEntity} {...newProps} />;
  }

  if (error) {
    return (
      <ErrorPage noLink text="Optimize could not be loaded, please make sure the server is running">
        <Button kind="ghost" onClick={() => window.location.reload(true)}>
          Reload
        </Button>
      </ErrorPage>
    );
  }

  return (
    <ConfigProvider>
      <TranslationProvider>
        <Theme>
          <Router getUserConfirmation={SaveGuard.getUserConfirmation}>
            <div className="Root-container">
              <ErrorBoundary>
                <UserProvider>
                  <DocsProvider>
                    <Switch>
                      <PrivateRoute exact path="/" component={Processes} />
                      <PrivateRoute path="/analysis" component={Analysis} />
                      <Route exact path="/share/:type/:id" component={Sharing} />
                      <PrivateRoute
                        path="/(report|dashboard/instant|dashboard|collection|processes/report)/*"
                        render={renderEntity}
                      />
                      <PrivateRoute exact path="/collections" component={Home} />
                      <Route path="/logout" component={Logout} />
                      <PrivateRoute path="*" component={ErrorPage} />
                    </Switch>
                  </DocsProvider>
                  <Tracking />
                  <Onboarding />
                </UserProvider>
              </ErrorBoundary>
            </div>
            <SaveGuard />
            <Prompt />
          </Router>
          <Notifications />
        </Theme>
      </TranslationProvider>
    </ConfigProvider>
  );
}
