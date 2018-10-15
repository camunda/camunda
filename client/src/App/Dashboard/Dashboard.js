import React, {Component, Fragment} from 'react';

import Header from '../Header';

import MetricPanel from './MetricPanel';
import MetricTile from './MetricTile';

import {fetchWorkflowInstancesCount} from 'modules/api/instances';
import {parseFilterForRequest} from 'modules/utils/filter';
import {FILTER_SELECTION, INSTANCES_LABELS} from 'modules/constants';

import * as Styled from './styled.js';

class Dashboard extends Component {
  state = {
    running: 0,
    active: 0,
    incidents: 0
  };

  componentDidMount = async () => {
    const counts = await this.fetchCounts();
    this.setState({...counts});
  };

  fetchCounts = async () => {
    return {
      running: await fetchWorkflowInstancesCount(
        parseFilterForRequest(FILTER_SELECTION.running)
      ),
      active: await fetchWorkflowInstancesCount(
        parseFilterForRequest(FILTER_SELECTION.active)
      ),
      incidents: await fetchWorkflowInstancesCount(
        parseFilterForRequest(FILTER_SELECTION.incidents)
      )
    };
  };

  render() {
    const {running, incidents} = this.state;
    const tiles = ['running', 'active', 'incidents'];
    return (
      <Fragment>
        <Header
          active="dashboard"
          runningInstancesCount={running}
          incidentsCount={incidents}
        />
        <Styled.Dashboard>
          <MetricPanel>
            {tiles.map(tile => (
              <MetricTile
                key={tile}
                value={this.state[tile]}
                label={INSTANCES_LABELS[tile]}
                type={tile}
              />
            ))}
          </MetricPanel>
        </Styled.Dashboard>
      </Fragment>
    );
  }
}

export default Dashboard;
