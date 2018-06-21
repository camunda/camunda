import React, {Component} from 'react';

import Header from '../Header';

import MetricPanel from './MetricPanel';
import MetricTile from './MetricTile';

import {fetchInstancesCount} from './api';

import * as Styled from './styled.js';

export default class Dashboard extends Component {
  state = {
    instances: 0,
    active: 0,
    incidents: 0
  };

  fetchCounts = async () => {
    return {
      instances: await fetchInstancesCount(),
      active: await fetchInstancesCount('active'),
      incidents: await fetchInstancesCount('incidents')
    };
  };

  componentDidMount = async () => {
    const counts = await this.fetchCounts();
    this.setState({...counts});
  };

  render() {
    const {instances, active, incidents} = this.state;
    return (
      <Styled.Dashboard>
        <Header
          active="dashboard"
          instances={instances}
          filters={0}
          selections={0}
          incidents={incidents}
        />
        <MetricPanel>
          <MetricTile metric={instances} name="Instances running" />
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
