import React from 'react';
import {HashRouter as Router, Route, Switch} from 'react-router-dom';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';

import Authentication from './Authentication';
import Login from './Login';
import Dashboard from './Dashboard';
import Instances from './Instances';
import Instance from './Instance';
import GlobalStyles from './GlobalStyles';

// Development Utility Component to test the theming.
import ThemeToggle from 'modules/theme/ThemeToggle';

export default function App(props) {
  return (
    <ThemeProvider>
      <CollapsablePanelProvider>
        <GlobalStyles />
        <ThemeToggle />
        <Router>
          <Switch>
            <Route path="/login" component={Login} />
            <Authentication>
              <Route exact path="/" component={Dashboard} />
              <Route exact path="/instances" component={Instances} />
              <Route exact path="/instances/:id" component={Instance} />
            </Authentication>
          </Switch>
        </Router>
      </CollapsablePanelProvider>
    </ThemeProvider>
  );
}
