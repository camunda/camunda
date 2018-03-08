import React from 'react';
import {BrowserRouter as Router, Route} from 'react-router-dom';

import {
  PrivateRoute,
  Header,
  Footer,
  Login,
  Home,
  Dashboards,
  Reports,
  Report,
  Dashboard,
  Analysis,
  Alerts,
  Sharing
} from './components';

import {ErrorBoundary} from 'components';

const mainWrapped = Component => props => (
  <main>
    <ErrorBoundary>
      <Component {...props} />
    </ErrorBoundary>
  </main>
);

const headered = Component => props => {
  const WrappedComponent = mainWrapped(Component);

  return (
    <React.Fragment>
      <Header name="Camunda Optimize" />
      <WrappedComponent {...props} />
      <Footer version="2.0.0" />
    </React.Fragment>
  );
};

const App = () => (
  <Router>
    <div className="Root-container">
      <Route exact path="/login" component={mainWrapped(Login)} />
      <PrivateRoute exact path="/" component={headered(Home)} />
      <PrivateRoute exact path="/dashboards" component={headered(Dashboards)} />
      <PrivateRoute exact path="/reports" component={headered(Reports)} />
      <PrivateRoute exact path="/analysis" component={headered(Analysis)} />
      <PrivateRoute exact path="/alerts" component={headered(Alerts)} />
      <Route exact path="/share/:type/:id" component={mainWrapped(Sharing)} />
      <PrivateRoute path="/report/:id/:viewMode?" component={headered(Report)} />
      <PrivateRoute path="/dashboard/:id/:viewMode?" component={headered(Dashboard)} />
    </div>
  </Router>
);

export default App;
