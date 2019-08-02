/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {HashRouter as Router, Route} from 'react-router-dom';
import {init} from 'translation';

import {
  PrivateRoute,
  Header,
  Footer,
  Overview,
  Alerts,
  Report,
  Dashboard,
  Analysis,
  Sharing
} from './components';

import {ErrorBoundary, LoadingIndicator} from 'components';

import {Notifications, addNotification} from 'notifications';

import {Provider as Theme} from 'theme';
import {withErrorHandling} from 'HOC';

class App extends React.Component {
  state = {
    error: false,
    translationLoaded: false
  };

  async componentDidMount() {
    this.props.mightFail(
      init(),
      () => this.setState({translationLoaded: true}),
      () => {
        this.setState({error: true}, () => {
          addNotification({
            text: `Optimize could not be loaded, please make sure the server is running.`,
            type: 'error'
          });
        });
      }
    );
  }

  render() {
    if (!this.state.translationLoaded) {
      return <LoadingIndicator />;
    }

    return (
      <Theme>
        <Router>
          <Route
            path="/"
            render={({location: {pathname}}) => {
              if (this.state.error) {
                return;
              }

              const hideHeader = pathname.indexOf('/share') === 0;

              return (
                <div className="Root-container">
                  {!hideHeader && <Header name="Camunda Optimize" />}
                  <main>
                    <ErrorBoundary>
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
  }
}

export default withErrorHandling(App);
