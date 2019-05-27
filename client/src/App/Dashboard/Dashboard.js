/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component, Fragment} from 'react';

import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';

import Header from '../Header';
import MetricPanel from './MetricPanel';
import InstancesByWorkflow from './InstancesByWorkflow';
import IncidentsByError from './IncidentsByError';
import EmptyIncidents from './EmptyIncidents';
import {fetchWorkflowCoreStatistics} from 'modules/api/instances';
import {
  fetchInstancesByWorkflow,
  fetchIncidentsByError
} from 'modules/api/incidents/incidents';
import {PAGE_TITLE} from 'modules/constants';

import * as Styled from './styled.js';

class Dashboard extends Component {
  state = {
    counts: {
      data: {
        running: 0,
        active: 0,
        withIncidents: 0
      },
      errors: null
    },
    incidents: {
      byWorkflow: {data: [], error: null},
      byError: {data: [], error: null}
    },
    isDataLoaded: false
  };

  componentDidMount = async () => {
    document.title = PAGE_TITLE.DASHBOARD;
    const counts = await fetchWorkflowCoreStatistics();
    const incidents = await this.fetchIncidents();
    this.setState({counts, incidents, isDataLoaded: true});
  };

  fetchIncidents = async () => {
    return {
      byWorkflow: await fetchInstancesByWorkflow(),
      byError: await fetchIncidentsByError()
    };
  };

  renderEmptyList = (incidentsState, type) => {
    const message =
      type === 'workflow'
        ? 'Instances by Workflow could not be fetched.'
        : 'Incidents by Error Message could not be fetched.';

    if (incidentsState.error) {
      return <EmptyIncidents type="warning" label={message} />;
    }

    if (incidentsState.data && incidentsState.data.length === 0) {
      return <EmptyIncidents type="info" label="There are no Workflows." />;
    }
  };

  render() {
    const {data} = this.state.counts;
    const {active, running, withIncidents} = data;
    return (
      <Fragment>
        <Header
          active="dashboard"
          runningInstancesCount={running}
          activeInstancesCount={active}
          incidentsCount={withIncidents}
        />
        <Styled.Dashboard>
          <VisuallyHiddenH1>Camunda Operate Dashboard</VisuallyHiddenH1>
          <MetricPanel
            runningInstancesCount={running}
            activeInstancesCount={active}
            incidentsCount={withIncidents}
          />
          <Styled.TitleWrapper>
            <Styled.Tile data-test="instances-byWorkflow">
              <Styled.TileTitle>Instances by Workflow</Styled.TileTitle>
              <Styled.TileContent>
                {this.state.isDataLoaded &&
                  this.renderEmptyList(
                    this.state.incidents.byWorkflow,
                    'workflow'
                  )}
                {this.state.isDataLoaded &&
                  Boolean(this.state.incidents.byWorkflow.data.length) && (
                    <InstancesByWorkflow
                      incidents={this.state.incidents.byWorkflow.data}
                    />
                  )}
              </Styled.TileContent>
            </Styled.Tile>
            <Styled.Tile data-test="incidents-byError">
              <Styled.TileTitle>Incidents by Error Message</Styled.TileTitle>
              <Styled.TileContent>
                {this.state.isDataLoaded &&
                  this.renderEmptyList(this.state.incidents.byError, 'error')}
                {this.state.isDataLoaded &&
                  Boolean(this.state.incidents.byError.data.length) && (
                    <IncidentsByError
                      incidents={this.state.incidents.byError.data}
                    />
                  )}
              </Styled.TileContent>
            </Styled.Tile>
          </Styled.TitleWrapper>
        </Styled.Dashboard>
      </Fragment>
    );
  }
}

export default Dashboard;
