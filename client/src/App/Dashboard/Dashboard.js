import React, {Component} from 'react';

import Header from '../Header';

import MetricPanel from './MetricPanel';
import MetricTile from './MetricTile';

import {fetchWorkflowInstancesCount} from './api';
import {parseFilterForRequest} from 'modules/utils/filter';
import {FILTER_SELECTION} from 'modules/constants/filter';

import withSharedState from 'modules/components/withSharedState';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

class Dashboard extends Component {
  static propTypes = {
    storeStateLocally: PropTypes.func.isRequired,
    getStateLocally: PropTypes.func.isRequired
  };

  state = {
    instances: 0,
    active: 0,
    incidents: 0
  };

  componentDidMount = async () => {
    const counts = await this.fetchCounts();
    this.props.storeStateLocally({
      instances: counts.instances,
      incidents: counts.incidents
    });
    this.setState({...counts});
  };

  fetchCounts = async () => {
    return {
      instances: await fetchWorkflowInstancesCount(
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
    const {filterCount} = this.props.getStateLocally();
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
            <MetricTile
              metric={instances}
              name="Running Instances"
              type="running"
            />
            <MetricTile
              metric={active}
              name="Active"
              metricColor="allIsWell"
              type="active"
            />
            <MetricTile
              metric={incidents}
              name="Incidents"
              metricColor="incidentsAndErrors"
              type="incidents"
            />
          </MetricPanel>
        </Styled.Dashboard>
      </React.Fragment>
    );
  }
}

export default withSharedState(Dashboard);
