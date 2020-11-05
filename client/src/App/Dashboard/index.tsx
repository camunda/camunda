/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {Component} from 'react';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import {MetricPanel} from './MetricPanel';
import {InstancesByWorkflow} from './InstancesByWorkflow';
import {IncidentsByError} from './IncidentsByError';
import {PAGE_TITLE} from 'modules/constants';
import Copyright from 'modules/components/Copyright';
import {statisticsStore} from 'modules/stores/statistics';
import {observer} from 'mobx-react';

import * as Styled from './styled';

const Dashboard = observer(
  class Dashboard extends Component {
    componentDidMount = () => {
      document.title = PAGE_TITLE.DASHBOARD;
    };

    render() {
      const {isFailed} = statisticsStore.state;

      return (
        <Styled.Dashboard>
          <VisuallyHiddenH1>Camunda Operate Dashboard</VisuallyHiddenH1>
          <Styled.MetricPanelWrapper>
            {isFailed ? (
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
          <Styled.TileWrapper>
            <Styled.Tile>
              <Styled.TileTitle>Instances by Workflow</Styled.TileTitle>
              <Styled.TileContent>
                <InstancesByWorkflow />
              </Styled.TileContent>
            </Styled.Tile>
            <Styled.Tile>
              <Styled.TileTitle>Incidents by Error Message</Styled.TileTitle>
              <Styled.TileContent>
                <IncidentsByError />
              </Styled.TileContent>
            </Styled.Tile>
          </Styled.TileWrapper>
          <Styled.Footer>
            <Copyright />
          </Styled.Footer>
        </Styled.Dashboard>
      );
    }
  }
);

export {Dashboard};
