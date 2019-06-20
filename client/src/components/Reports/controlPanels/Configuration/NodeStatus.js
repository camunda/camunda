/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Select} from 'components';

export default function NodeStatus({
  report: {
    data: {groupBy, configuration}
  },
  onChange
}) {
  if (groupBy && ['flowNodes', 'assignee', 'candidateGroup'].includes(groupBy.type)) {
    return (
      <fieldset className="NodeStatus">
        <legend>Flow Node Status</legend>
        <Select
          value={configuration.flowNodeExecutionState}
          onChange={value => onChange({flowNodeExecutionState: {$set: value}}, true)}
        >
          <Select.Option value="running">Running</Select.Option>
          <Select.Option value="completed">Completed</Select.Option>
          <Select.Option value="all">All</Select.Option>
        </Select>
      </fieldset>
    );
  }
  return null;
}
