/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {HashRouter as Router, Route} from 'react-router-dom';

import {
  PrivateRoute,
  Header,
  Footer,
  Login,
  Overview,
  Alerts,
  Report,
  Dashboard,
  Analysis,
  Sharing
} from './components';

import {ErrorBoundary} from 'components';

import {Notifications} from 'notifications';

import {Provider as Theme} from 'theme';

const App = () => (
  <Theme>
    <Router>
      <Route
        path="/"
        render={({location: {pathname}}) => {
          const hideHeader = pathname.indexOf('/login') === 0 || pathname.indexOf('/share') === 0;

          return (
            <div className="Root-container">
              {!hideHeader && <Header name="Camunda Optimize" />}
              <main>
                <ErrorBoundary>
                  <Route exact path="/login" component={Login} />
                  <PrivateRoute exact path="/" component={Overview} />
                  <PrivateRoute exact path="/analysis" component={Analysis} />
                  <PrivateRoute exact path="/alerts" component={Alerts} />
                  <Route exact path="/share/:type/:id" component={Sharing} />
                  <PrivateRoute path="/report/:id/:viewMode?" component={Report} />
                  <PrivateRoute path="/dashboard/:id/:viewMode?" component={Dashboard} />
                </ErrorBoundary>
              </main>
              {!hideHeader && <Footer />}
            </div>
          );
        }}
      />
    </Router>
    <Notifications />
  </Theme>
);

export default App;
