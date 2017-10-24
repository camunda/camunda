import React from 'react';
import ReactDOM from 'react-dom';
import {
  BrowserRouter as Router,
  Route
} from 'react-router-dom'

import 'style.css';

import {
  PrivateRoute,
  Header,
  Footer,
  Login,
  Home,
  Dashboards,
  Reports,
  Report,
  Dashboard
} from './components';

ReactDOM.render(<Router>
  <div>
    <Header name="Camunda Optimize" />
    <Route exact path="/login" component={Login} />
    <PrivateRoute exact path="/" component={Home} />
    <PrivateRoute exact path="/dashboards" component={Dashboards} />
    <PrivateRoute exact path="/reports" component={Reports} />
    <PrivateRoute exact path="/report/:id" component={Report} />
    <PrivateRoute exact path="/dashboard/:id" component={Dashboard} />
    <Footer version="2.0.0" />
  </div>
</Router>, document.getElementById('root'));
