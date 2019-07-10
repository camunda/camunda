/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Select} from 'components';

export default function DistributedBy({
  report: {
    data: {configuration, groupBy, visualization}
  },
  onChange
}) {
  if (groupBy && ['assignee', 'candidateGroup'].includes(groupBy.type)) {
    return (
      <fieldset className="DistributedBy">
        <legend>Distributed By</legend>
        <Select
          value={configuration.distributedBy}
          onChange={value => {
            if (value === 'userTask' && (visualization === 'pie' || visualization === 'line')) {
              onChange(
                {visualization: {$set: 'bar'}, configuration: {distributedBy: {$set: value}}},
                true
              );
            } else {
              onChange({configuration: {distributedBy: {$set: value}}}, true);
            }
          }}
        >
          <Select.Option value="none">None</Select.Option>
          <Select.Option value="userTask">User Task</Select.Option>
        </Select>
      </fieldset>
    );
  }
  return null;
}
