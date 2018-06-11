import React, {Component} from 'react';
import {MetricPanel} from './MetricPanel';

// import {loadDashboard} from './service.js';
import * as Styled from './styled.js';

class Dashboard extends Component {
  render() {
    const metricTiles = [
      {metric: 62905, name: 'Instances running', metricColor: 'themed'},
      {metric: 436432, name: 'Active', metricColor: 'allIsWell'},
      {metric: 193473, name: 'Incidents', metricColor: 'incidentsAndErrors'}
    ];

    return (
      <Styled.Dashboard>
        <MetricPanel metricTiles={metricTiles} />
      </Styled.Dashboard>
    );
  }
}

export default Dashboard;
