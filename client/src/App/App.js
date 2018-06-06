import React from 'react';
import {BrowserRouter as Router, Route} from 'react-router-dom';

import {ThemeProvider} from 'theme';

import {Header} from './Header';
import {Dashboard} from './Dashboard';

const Home = () => (
  <ThemeProvider>
    <Header />
    <Dashboard />
  </ThemeProvider>
);

class App extends React.Component {
  render() {
    return (
      <Router>
        <React.Fragment>
          <Route exact path="/" component={Home} />
        </React.Fragment>
      </Router>
    );
  }
}

export default App;
