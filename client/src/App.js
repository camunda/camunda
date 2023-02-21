/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {HashRouter as Router, Route, Switch, matchPath} from 'react-router-dom';
import {initTranslation} from 'translation';

import {
  PrivateRoute,
  Home,
  Collection,
  Report,
  Dashboard,
  Analysis,
  Events,
  Process,
  Sharing,
  License,
  WithLicense,
  Logout,
  Processes,
  ProcessReport,
} from './components';

import {ErrorBoundary, LoadingIndicator, ErrorPage, Button} from 'components';

import {Notifications} from 'notifications';
import {SaveGuard} from 'saveGuard';
import {Prompt} from 'prompt';
import {Tracking} from 'tracking';
import {Onboarding} from 'onboarding';

import {Provider as Theme} from 'theme';
import {withErrorHandling, UserProvider, DocsProvider} from 'HOC';

class App extends React.Component {
  state = {
    translationLoaded: false,
  };

  async componentDidMount() {
    this.props.mightFail(initTranslation(), () => this.setState({translationLoaded: true}));
  }

  renderEntity = (props) => {
    const components = {
      report: Report,
      'dashboard/instant': Dashboard,
      dashboard: Dashboard,
      'events/processes': Process,
      'processes/report': ProcessReport,
      collection: Collection,
    };
    const entities = [
      'processes/report',
      'report',
      'dashboard/instant',
      'dashboard',
      'collection',
      'events/processes',
    ];
    let Component, newProps;
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
        break;
      }
    }
    return <Component {...newProps} />;
  };

  render() {
    if (this.props.error) {
      return (
        <ErrorPage
          noLink
          text="Optimize could not be loaded, please make sure the server is running"
        >
          <Button link onClick={() => window.location.reload(true)}>
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
        <Router getUserConfirmation={SaveGuard.getUserConfirmation}>
          <WithLicense>
            <div className="Root-container">
              <ErrorBoundary>
                <UserProvider>
                  <DocsProvider>
                    <Switch>
                      <PrivateRoute exact path="/" component={Home} />
                      <PrivateRoute path="/analysis" component={Analysis} />
                      <PrivateRoute exact path="/events/processes" component={Events} />
                      <PrivateRoute path="/events/ingested" component={Events} />
                      <Route exact path="/share/:type/:id" component={Sharing} />
                      <PrivateRoute
                        path="/(report|dashboard/instant|dashboard|collection|events/processes|processes/report)/*"
                        render={this.renderEntity}
                      />
                      <PrivateRoute path="/processes" component={Processes} />
                      <Route path="/license" component={License} />
                      <Route path="/logout" component={Logout} />
                      <PrivateRoute path="*" component={ErrorPage} />
                    </Switch>
                  </DocsProvider>
                  <Tracking />
                  <Onboarding />
                </UserProvider>
              </ErrorBoundary>
            </div>
          </WithLicense>
          <SaveGuard />
          <Prompt />
        </Router>
        <Notifications />
      </Theme>
    );
  }
}

export default withErrorHandling(App);
