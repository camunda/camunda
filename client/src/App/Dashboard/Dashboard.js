import React, {Component} from 'react';

import MetricPanel from './MetricPanel';
import MetricTile from './MetricTile';

import * as api from './api';

import * as Styled from './styled.js';

export default class Dashboard extends Component {
  state = {
    running: 0,
    active: 0,
    incidents: 0
  };

  componentDidMount = async () => {
    const running = await api.loadRunningInst();
    const active = await api.loadInstWithIncidents();
    const incidents = await api.loadInstWithoutIncidents();
    this.setState({running, active, incidents});
  };

  render() {
    const {running, active, incidents} = this.state;
    return (
      <Styled.Dashboard>
        <MetricPanel>
          <MetricTile
            metric={running}
            name="Instances running"
            metricColor="themed"
          />
          <MetricTile metric={active} name="Active" metricColor="allIsWell" />
          <MetricTile
            metric={incidents}
            name="Incidents"
            metricColor="incidentsAndErrors"
          />
        </MetricPanel>
      </Styled.Dashboard>
    );
  }
}
