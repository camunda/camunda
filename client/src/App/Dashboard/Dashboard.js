import React, {Component, Fragment} from 'react';

import Header from '../Header';

import MetricPanel from './MetricPanel';
import MetricTile from './MetricTile';

import {fetchWorkflowInstancesCount} from 'modules/api/instances';
import {parseFilterForRequest} from 'modules/utils/filter';
import {FILTER_SELECTION, LABELS} from 'modules/constants/filter';

import withSharedState from 'modules/components/withSharedState';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

class Dashboard extends Component {
  static propTypes = {
    storeStateLocally: PropTypes.func.isRequired,
    getStateLocally: PropTypes.func.isRequired
  };

  state = {
    running: 0,
    active: 0,
    incidents: 0
  };

  componentDidMount = async () => {
    const counts = await this.fetchCounts();
    this.props.storeStateLocally({
      running: counts.running,
      incidents: counts.incidents
    });
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
    const {filterCount} = this.props.getStateLocally();
    const {running, incidents} = this.state;
    const tiles = ['running', 'active', 'incidents'];
    return (
      <Fragment>
        <Header
          active="dashboard"
          instances={running}
          filters={filterCount || 0}
          selections={0}
          incidents={incidents}
        />
        <Styled.Dashboard>
          <MetricPanel>
            {tiles.map(tile => (
              <MetricTile
                key={tile}
                value={this.state[tile]}
                label={LABELS[tile]}
                type={tile}
              />
            ))}
          </MetricPanel>
        </Styled.Dashboard>
      </Fragment>
    );
  }
}

export default withSharedState(Dashboard);
