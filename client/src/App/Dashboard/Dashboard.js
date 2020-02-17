/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component, Fragment} from 'react';
import PropTypes from 'prop-types';

import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import {withData} from 'modules/DataManager';

import MetricPanel from './MetricPanel';
import InstancesByWorkflow from './InstancesByWorkflow';
import IncidentsByError from './IncidentsByError';
import Skeleton from './Skeleton';

import {PAGE_TITLE, LOADING_STATE} from 'modules/constants';

import EmptyPanel from 'modules/components/EmptyPanel';
import Copyright from 'modules/components/Copyright';
import * as Styled from './styled.js';

import {MESSAGES, INCIDENTS_BY_ERROR, INSTANCES_BY_WORKFLOW} from './constants';

export class Dashboard extends Component {
  static propTypes = {
    dataManager: PropTypes.object
  };

  state = {
    counts: {
      errors: null
    },
    instancesByWorkflow: {data: [], error: null, isLoading: true},
    incidentsByError: {data: [], error: null, isLoading: true}
  };

  componentDidMount = async () => {
    document.title = PAGE_TITLE.DASHBOARD;

    const {dataManager} = this.props;

    dataManager.subscribe(this.subscriptions);
    dataManager.getInstancesByWorkflow();
    dataManager.getIncidentsByError();
  };

  componentWillUnmount = async () => {
    const {dataManager} = this.props;
    dataManager.unsubscribe(this.subscriptions);
  };

  subscriptions = {
    LOAD_INSTANCES_BY_WORKFLOW: ({state, response}) => {
      if (
        state === LOADING_STATE.LOADED ||
        state === LOADING_STATE.LOAD_FAILED
      ) {
        this.setState({
          instancesByWorkflow: {...response, isLoading: false}
        });
      }
    },
    LOAD_INCIDENTS_BY_ERROR: ({state, response}) => {
      if (
        state === LOADING_STATE.LOADED ||
        state === LOADING_STATE.LOAD_FAILED
      ) {
        this.setState({
          incidentsByError: {...response, isLoading: false}
        });
      }
    }
  };

  renderPanel = (type, state) => {
    if (state.error) {
      return <EmptyPanel type="warning" label={MESSAGES[type].error} />;
    } else if (state.isLoading) {
      return <EmptyPanel type="skeleton" Skeleton={Skeleton} rowHeight={55} />;
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
      instancesByWorkflow,
      incidentsByError,
      counts: {error: countsError}
    } = this.state;

    return (
      <Fragment>
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
              <MetricPanel />
            )}
          </Styled.MetricPanelWrapper>
          <Styled.TitleWrapper>
            <Styled.Tile data-test="instances-byWorkflow">
              <Styled.TileTitle>Instances by Workflow</Styled.TileTitle>
              <Styled.TileContent>
                {this.renderPanel(INSTANCES_BY_WORKFLOW, instancesByWorkflow)}
              </Styled.TileContent>
            </Styled.Tile>
            <Styled.Tile data-test="incidents-byError">
              <Styled.TileTitle>Incidents by Error Message</Styled.TileTitle>
              <Styled.TileContent>
                {this.renderPanel(INCIDENTS_BY_ERROR, incidentsByError)}
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

export default withData(Dashboard);
