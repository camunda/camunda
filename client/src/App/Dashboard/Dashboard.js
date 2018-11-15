import React, {Component, Fragment} from 'react';

import TransparentHeading from 'modules/components/TransparentHeading';

import Header from '../Header';
import MetricPanel from './MetricPanel';
import MetricTile from './MetricTile';
import IncidentsByWorkflow from './IncidentsByWorkflow';

import {fetchWorkflowInstancesCount} from 'modules/api/instances';
import {fetchIncidentsByWorkflow} from 'modules/api/incidents';
import {parseFilterForRequest} from 'modules/utils/filter';
import {
  FILTER_SELECTION,
  DASHBOARD_LABELS,
  PAGE_TITLE
} from 'modules/constants';

import * as Styled from './styled.js';

class Dashboard extends Component {
  state = {
    counts: {
      running: 0,
      active: 0,
      incidents: 0
    },
    incidents: {
      byWorkflow: []
    }
  };

  componentDidMount = async () => {
    document.title = PAGE_TITLE.DASHBOARD;
    const counts = await this.fetchCounts();
    const incidents = await this.fetchIncidents();

    this.setState({counts, incidents});
  };

  fetchIncidents = async () => {
    return {
      byWorkflow: await fetchIncidentsByWorkflow()
    };
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
    const {running, incidents} = this.state.counts;
    const tiles = ['running', 'active', 'incidents'];
    return (
      <Fragment>
        <TransparentHeading>Camunda Operate Dashboard</TransparentHeading>
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
                value={this.state.counts[tile]}
                label={DASHBOARD_LABELS[tile]}
                type={tile}
              />
            ))}
          </MetricPanel>
          <Styled.Tile data-test="incidents-byWorkflow">
            <Styled.TileTitle>Incidents by Workflow</Styled.TileTitle>
            <Styled.TileContent>
              <IncidentsByWorkflow
                incidents={this.state.incidents.byWorkflow}
              />
            </Styled.TileContent>
          </Styled.Tile>
        </Styled.Dashboard>
      </Fragment>
    );
  }
}

export default Dashboard;
