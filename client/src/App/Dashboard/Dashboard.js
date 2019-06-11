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
import {fetchWorkflowCoreStatistics} from 'modules/api/instances';
import {
  fetchInstancesByWorkflow,
  fetchIncidentsByError
} from 'modules/api/incidents/incidents';
import {PAGE_TITLE} from 'modules/constants';
import EmptyPanel from 'modules/components/EmptyPanel';
import Copyright from 'modules/components/Copyright';
import * as Styled from './styled.js';
import {MESSAGES, INCIDENTS_BY_ERROR, INSTANCES_BY_WORKFLOW} from './service';

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

  renderPanel = (type, state) => {
    if (state.error) {
      return <EmptyPanel type="warning" label={MESSAGES[type].error} />;
    } else if (state.data.length === 0) {
      return <EmptyPanel type="info" label={MESSAGES[type].noData} />;
    } else if (state.data.length > 0 && type === INSTANCES_BY_WORKFLOW) {
      return <InstancesByWorkflow incidents={state.data} />;
    } else if (state.data.length > 0 && type === INCIDENTS_BY_ERROR) {
      return <IncidentsByError incidents={state.data} />;
    }
  };

  render() {
    const {
      incidents: {byError, byWorkflow},
      counts: {
        data: {active, running, withIncidents},
        error: countsError
      }
    } = this.state;

    return (
      <Fragment>
        <Header
          active="dashboard"
          runningInstancesCount={running || 0}
          activeInstancesCount={active || 0}
          incidentsCount={withIncidents || 0}
        />
        <Styled.Dashboard>
          <VisuallyHiddenH1>Camunda Operate Dashboard</VisuallyHiddenH1>
          <Styled.MetricPanelWrapper>
            {countsError ? (
              <Styled.EmptyMetricPanelWrapper>
                <Styled.EmptyMetricPanel
                  label="Workflow statistics could not be fetched."
                  type="warning"
                />
              </Styled.EmptyMetricPanelWrapper>
            ) : (
              <MetricPanel
                runningInstancesCount={running}
                activeInstancesCount={active}
                incidentsCount={withIncidents}
              />
            )}
          </Styled.MetricPanelWrapper>
          <Styled.TitleWrapper>
            <Styled.Tile data-test="instances-byWorkflow">
              <Styled.TileTitle>Instances by Workflow</Styled.TileTitle>
              <Styled.TileContent>
                {this.state.isDataLoaded &&
                  this.renderPanel(INSTANCES_BY_WORKFLOW, byWorkflow)}
              </Styled.TileContent>
            </Styled.Tile>
            <Styled.Tile data-test="incidents-byError">
              <Styled.TileTitle>Incidents by Error Message</Styled.TileTitle>
              <Styled.TileContent>
                {this.state.isDataLoaded &&
                  this.renderPanel(INCIDENTS_BY_ERROR, byError)}
              </Styled.TileContent>
            </Styled.Tile>
          </Styled.TitleWrapper>

          <Styled.Footer>
            <Copyright />
          </Styled.Footer>
        </Styled.Dashboard>
      </Fragment>
    );
  }
}

export default Dashboard;
