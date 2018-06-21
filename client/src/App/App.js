import React from 'react';
import {BrowserRouter as Router, Route} from 'react-router-dom';

import {ThemeProvider} from 'modules/theme';

import Authentication from './Authentication';
import Login from './Login';
import Dashboard from './Dashboard';
import Instances from './Instances';
import Instance from './Instance';

// Development Utility Component to test the theming.
import ThemeToggle from 'modules/theme/ThemeToggle';

const InstancesPage = () => (
  <React.Fragment>
    <Instances />
  </React.Fragment>
);

export default function App(props) {
  return (
    <ThemeProvider>
      <ThemeToggle />
      <Router>
        <Authentication>
          <Route path="/login" component={Login} />
          <Route exact path="/" component={Dashboard} />
          <Route exact path="/instances" component={InstancesPage} />
          <Route exact path="/instances/:id" component={Instance} />
        </Authentication>
      </Router>
    </ThemeProvider>
  );
}
