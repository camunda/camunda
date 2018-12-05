import React from 'react';

import Home from './Home';
import Reports from './Reports';
import Dashboards from './Dashboards';
import Alerts from './Alerts';

import './Overview.scss';

const withOverview = Component => props => (
  <div className="Overview">
    <Component {...props} />
  </div>
);

const Overview = {
  Home: withOverview(Home),
  Reports: withOverview(Reports),
  Dashboards: withOverview(Dashboards),
  Alerts: withOverview(Alerts)
};

export default Overview;
