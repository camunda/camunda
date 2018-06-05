import React from 'react';
import {BrowserRouter as Router, Route} from 'react-router-dom';

import {ThemeProvider} from 'theme';

import {Authentication} from './Authentication';
import {Login} from './Login';
import {Header} from './Header';
import {Dashboard} from './Dashboard';

const Home = () => (
  <React.Fragment>
    <Header
      active="dashboard"
      instances={14576}
      filters={9263}
      selections={24}
      incidents={328}
    />
    <Dashboard />
  </React.Fragment>
);

export default function App(props) {
  return (
    <ThemeProvider>
      <Router>
        <Authentication>
          <Route path="/login" component={Login} />
          <Route exact path="/" component={Home} />
        </Authentication>
      </Router>
    </ThemeProvider>
  );
}
