import React from 'react';
import ReactDOM from 'react-dom';
import {
  BrowserRouter as Router,
  Route
} from 'react-router-dom'

import './style.css';
import 'polyfills';

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
  Analysis
} from './components';

import {ErrorBoundary} from 'components';

ReactDOM.render(<Router>
  <div style={{height: '100%', width:'100%'}}>
    <Header name="Camunda Optimize" />
    <main style={{height: '100%', width:'100%'}}>
      <ErrorBoundary>
        <Route exact path="/login" component={Login} />
        <PrivateRoute exact path="/" component={Home} />
        <PrivateRoute exact path="/dashboards" component={Dashboards} />
        <PrivateRoute exact path="/reports" component={Reports} />
        <PrivateRoute path="/report/:id/:viewMode?" component={Report} />
        <PrivateRoute path="/dashboard/:id/:viewMode?" component={Dashboard} />
        <PrivateRoute path="/analysis" component={Analysis} />
      </ErrorBoundary>
    </main>
    <Footer version="2.0.0" />
  </div>
</Router>, document.getElementById('root'));
