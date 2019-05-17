/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled.js';

function getUrl({filter, hasFinishedInstances}) {
  if (hasFinishedInstances) {
    Object.assign(filter, {
      completed: true,
      canceled: true
    });
  }

  return `/instances?filter=${JSON.stringify(filter)}`;
}

export default function MetricPanel({runningInstancesCount, incidentsCount}) {
  const totalRunningInstancesCount = runningInstancesCount + incidentsCount;

  return (
    <Styled.Panel>
      <Styled.Title
        to={getUrl({
          filter: {active: true, incidents: true},
          hasFinishedInstances: totalRunningInstancesCount === 0
        })}
      >
        {totalRunningInstancesCount} Running Instances in total
      </Styled.Title>
      <Styled.InstancesBar
        incidentsCount={incidentsCount}
        activeCount={runningInstancesCount}
        size="large"
      />
      <Styled.LabelContainer>
        <Styled.Label
          to={getUrl({
            filter: {incidents: true}
          })}
        >
          Instances with Incident
        </Styled.Label>
        <Styled.Label
          to={getUrl({
            filter: {active: true}
          })}
        >
          Active Instances
        </Styled.Label>
      </Styled.LabelContainer>
    </Styled.Panel>
  );
}

MetricPanel.propTypes = {
  runningInstancesCount: PropTypes.number,
  incidentsCount: PropTypes.number
};
