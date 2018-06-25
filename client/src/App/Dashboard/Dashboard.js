import React, {Component} from 'react';

import Header from '../Header';

import MetricPanel from './MetricPanel';
import MetricTile from './MetricTile';

import {fetchInstancesCount} from './api';

import withSharedState from 'modules/components/withSharedState';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

class Dashboard extends Component {
  state = {
    instances: 0,
    active: 0,
    incidents: 0
  };

  static propTypes = {
    storeState: PropTypes.func.isRequired,
    getState: PropTypes.func.isRequired
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
    this.props.storeState({
      instances: counts.instances,
      incidents: counts.incidents
    });
    this.setState({...counts});
  };

  render() {
    const {filterCount} = this.props.getState();
    const {instances, active, incidents} = this.state;
    return (
      <React.Fragment>
        <Header
          active="dashboard"
          instances={instances}
          filters={filterCount || 0}
          selections={0}
          incidents={incidents}
        />
        <Styled.Dashboard>
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
      </React.Fragment>
    );
  }
}

export default withSharedState(Dashboard);
