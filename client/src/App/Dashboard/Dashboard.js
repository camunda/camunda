/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component, Fragment} from 'react';

import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';

import Header from '../Header';
import MetricPanel from './MetricPanel';
import IncidentsByWorkflow from './IncidentsByWorkflow';
import IncidentsByError from './IncidentsByError';
import EmptyIncidents from './EmptyIncidents';
import {fetchWorkflowInstancesCount} from 'modules/api/instances';
import {
  fetchIncidentsByWorkflow,
  fetchIncidentsByError
} from 'modules/api/incidents/incidents';
import {parseFilterForRequest} from 'modules/utils/filter';
import {FILTER_SELECTION, PAGE_TITLE} from 'modules/constants';

import * as Styled from './styled.js';

class Dashboard extends Component {
  state = {
    counts: {
      running: 0,
      active: 0,
      incidents: 0
    },
    incidents: {
      byWorkflow: {data: [], error: null},
      byError: {date: [], error: null}
    },
    isDataLoaded: false
  };

  componentDidMount = async () => {
    document.title = PAGE_TITLE.DASHBOARD;
    const counts = await this.fetchCounts();
    const incidents = await this.fetchIncidents();

    this.setState({counts, incidents, isDataLoaded: true});
  };

  fetchIncidents = async () => {
    return {
      byWorkflow: await fetchIncidentsByWorkflow(),
      byError: await fetchIncidentsByError()
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

  renderEmptyList = (state, type) => {
    const message =
      type === 'workflow'
        ? 'Instances by Workflow could not be fetched.'
        : 'Incidents by Error Message could not be fetched.';

    if (state.error) {
      return <EmptyIncidents type="warning" label={message} />;
    }

    if (state.data.length === 0) {
      return <EmptyIncidents type="info" label="There are no Workflows." />;
    }
  };

  render() {
    const {running, incidents} = this.state.counts;

    return (
      <Fragment>
        <Header
          active="dashboard"
          runningInstancesCount={running}
          incidentsCount={incidents}
        />
        <Styled.Dashboard>
          <VisuallyHiddenH1>Camunda Operate Dashboard</VisuallyHiddenH1>
          <MetricPanel
            runningInstancesCount={running}
            incidentsCount={incidents}
          />
          <Styled.TitleWrapper>
            <Styled.Tile data-test="incidents-byWorkflow">
              <Styled.TileTitle>Instances by Workflow</Styled.TileTitle>
              <Styled.TileContent>
                {this.state.isDataLoaded &&
                  this.renderEmptyList(
                    this.state.incidents.byWorkflow,
                    'workflow'
                  )}
                {this.state.isDataLoaded &&
                  Boolean(this.state.incidents.byWorkflow.data.length) && (
                    <IncidentsByWorkflow
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
