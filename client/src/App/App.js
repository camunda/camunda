import React from 'react';
import {BrowserRouter as Router, Route} from 'react-router-dom';

import {ThemeProvider} from 'modules/theme';

import {Authentication} from './Authentication';
import {Login} from './Login';
import {Header} from './Header';
import {Dashboard} from './Dashboard';
import {Filter} from './Filter';

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

const FilterPage = () => (
  <React.Fragment>
    <Filter />
  </React.Fragment>
);

export default function App(props) {
  return (
    <ThemeProvider>
      <Router>
        <Authentication>
          <Route path="/login" component={Login} />
          <Route exact path="/" component={Home} />
          <Route exact path="/filter" component={FilterPage} />
        </Authentication>
      </Router>
    </ThemeProvider>
  );
}
