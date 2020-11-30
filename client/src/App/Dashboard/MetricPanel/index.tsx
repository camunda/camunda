/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {observer} from 'mobx-react';

import {
  Panel,
  Title,
  InstancesBar,
  SkeletonBar,
  LabelContainer,
  Label,
} from './styled';
import {statisticsStore} from 'modules/stores/statistics';
import {StatusMessage} from 'modules/components/StatusMessage';

function getUrl({filter, hasFinishedInstances}: any) {
  if (hasFinishedInstances) {
    Object.assign(filter, {
      completed: true,
      canceled: true,
    });
  }

  return `/instances?filter=${JSON.stringify(filter)}`;
}

const MetricPanel = observer(() => {
  const {running, active, withIncidents, status} = statisticsStore.state;

  if (status === 'error') {
    return (
      <StatusMessage variant="error">
        Workflow statistics could not be fetched
      </StatusMessage>
    );
  }

  return (
    <Panel data-testid="metric-panel">
      <Title
        data-testid="total-instances-link"
        to={getUrl({
          filter: {active: true, incidents: true},
          hasFinishedInstances: running === 0,
        })}
      >
        {status === 'fetched' && `${running} `}Running Instances in total
      </Title>
      {status === 'fetched' && (
        <InstancesBar
          incidentsCount={withIncidents}
          activeCount={active}
          size="large"
          barHeight={15}
        />
      )}
      {(status === 'initial' || status === 'first-fetch') && (
        <SkeletonBar data-testid="instances-bar-skeleton" />
      )}

      <LabelContainer>
        <Label
          data-testid="incident-instances-link"
          to={getUrl({
            filter: {incidents: true},
          })}
        >
          Instances with Incident
        </Label>
        <Label
          data-testid="active-instances-link"
          to={getUrl({
            filter: {active: true},
          })}
        >
          Active Instances
        </Label>
      </LabelContainer>
    </Panel>
  );
});

export {MetricPanel};
