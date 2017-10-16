import React from 'react';
import ReactDOM from 'react-dom';
import {
  BrowserRouter as Router,
  Route
} from 'react-router-dom'

import 'style.css';

import {PrivateRoute, Header, LogoutButton, Footer, Home, Login} from './components';

ReactDOM.render(<Router>
  <div>
    <Header name="Camunda Optimize">
      <LogoutButton />
    </Header>
    <Route exact path="/login" component={Login} />
    <PrivateRoute exact path="/" component={Home} />
    <Footer version="2.0.0" />
  </div>
</Router>, document.getElementById('root'));
