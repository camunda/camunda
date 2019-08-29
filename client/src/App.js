/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {HashRouter as Router, Route, Switch} from 'react-router-dom';
import {init} from 'translation';

import {
  PrivateRoute,
  Header,
  Footer,
  Home,
  Alerts,
  Report,
  Dashboard,
  Analysis,
  Sharing
} from './components';

import {ErrorBoundary, LoadingIndicator, ErrorPage, Button} from 'components';

import {Notifications} from 'notifications';

import {Provider as Theme} from 'theme';
import {withErrorHandling} from 'HOC';

class App extends React.Component {
  state = {
    translationLoaded: false
  };

  async componentDidMount() {
    this.props.mightFail(init(), () => this.setState({translationLoaded: true}));
  }

  render() {
    if (this.props.error) {
      return (
        <ErrorPage
          noLink
          text="Optimize could not be loaded, please make sure the server is running"
        >
          <Button variant="link" onClick={() => window.location.reload(true)}>
            Reload
          </Button>
        </ErrorPage>
      );
    }

    if (!this.state.translationLoaded) {
      return <LoadingIndicator />;
    }

    return (
      <Theme>
        <Router>
          <Route
            path="/"
            render={({location: {pathname}}) => {
              const hideHeader = pathname.indexOf('/share') === 0;

              return (
                <div className="Root-container">
                  {!hideHeader && <Header name="Camunda Optimize" />}
                  <main>
                    <ErrorBoundary>
                      <Switch>
                        <PrivateRoute exact path="/" component={Home} />
                        <PrivateRoute path="/analysis" component={Analysis} />
                        <PrivateRoute exact path="/alerts" component={Alerts} />
                        <Route exact path="/share/:type/:id" component={Sharing} />
                        <PrivateRoute path="/report/:id/:viewMode?" component={Report} />
                        <PrivateRoute path="/dashboard/:id/:viewMode?" component={Dashboard} />
                        <PrivateRoute path="*" component={ErrorPage} />
                      </Switch>
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
