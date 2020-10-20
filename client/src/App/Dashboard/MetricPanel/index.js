/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {observer} from 'mobx-react';

import * as Styled from './styled.js';
import {statistics} from 'modules/stores/statistics';

function getUrl({filter, hasFinishedInstances}) {
  if (hasFinishedInstances) {
    Object.assign(filter, {
      completed: true,
      canceled: true,
    });
  }

  return `/instances?filter=${JSON.stringify(filter)}`;
}

const MetricPanel = observer(() => {
  const {running, active, withIncidents, isLoaded} = statistics.state;
  return (
    <Styled.Panel data-testid="metric-panel">
      <Styled.Title
        data-testid="total-instances-link"
        to={getUrl({
          filter: {active: true, incidents: true},
          hasFinishedInstances: running === 0,
        })}
      >
        {isLoaded && `${running} `}Running Instances in total
      </Styled.Title>

      {isLoaded ? (
        <Styled.InstancesBar
          incidentsCount={withIncidents}
          activeCount={active}
          size="large"
          barHeight={15}
        />
      ) : (
        <Styled.SkeletonBar data-testid="instances-bar-skeleton" />
      )}

      <Styled.LabelContainer>
        <Styled.Label
          data-testid="incident-instances-link"
          to={getUrl({
            filter: {incidents: true},
          })}
        >
          Instances with Incident
        </Styled.Label>
        <Styled.Label
          data-testid="active-instances-link"
          to={getUrl({
            filter: {active: true},
          })}
        >
          Active Instances
        </Styled.Label>
      </Styled.LabelContainer>
    </Styled.Panel>
  );
});

export {MetricPanel};
