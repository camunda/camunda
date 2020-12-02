/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {HashRouter as Router, Route, Switch} from 'react-router-dom';

import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {NotificationProvider} from 'modules/notifications';

import Authentication from './Authentication';
import Header from './Header';
import {Login} from './Login';
import {Dashboard} from './Dashboard';
import {Instances} from './Instances';
import {Instance} from './Instance';
import GlobalStyles from './GlobalStyles';
import {NetworkStatusWatcher} from './NetworkStatusWatcher';
import {CommonUiContext} from 'modules/CommonUiContext';

function App() {
  return (
    <ThemeProvider>
      <NotificationProvider>
        <CollapsablePanelProvider>
          <GlobalStyles />
          <NetworkStatusWatcher />
          <CommonUiContext />
          <Router>
            <Switch>
              <Route path="/login" component={Login} />
              <Authentication>
                <Header />
                <Route exact path="/" component={Dashboard} />
                <Route exact path="/instances" component={Instances} />
                <Route exact path="/instances/:id" component={Instance} />
              </Authentication>
            </Switch>
          </Router>
        </CollapsablePanelProvider>
      </NotificationProvider>
    </ThemeProvider>
  );
}

export {App};
