/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Select} from 'components';

export default function UserTaskDurationTime({
  report: {
    data: {configuration, view}
  },
  onChange
}) {
  if (view && view.entity === 'userTask' && view.property === 'duration') {
    return (
      <fieldset className="UserTaskDurationTime">
        <legend>User Task Duration Time</legend>
        <Select
          value={configuration.userTaskDurationTime}
          onChange={value => onChange({userTaskDurationTime: {$set: value}}, true)}
        >
          <Select.Option value="idle">Idle</Select.Option>
          <Select.Option value="work">Work</Select.Option>
          <Select.Option value="total">Total</Select.Option>
        </Select>
      </fieldset>
    );
  }
  return null;
}
