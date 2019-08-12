/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Select} from 'components';
import {t} from 'translation';

export default function UserTaskDurationTime({
  report: {
    data: {configuration, view}
  },
  onChange
}) {
  if (view && view.entity === 'userTask' && view.property === 'duration') {
    return (
      <fieldset className="UserTaskDurationTime">
        <legend>{t('report.config.userTaskDuration.legend')}</legend>
        <Select
          value={configuration.userTaskDurationTime}
          onChange={value => onChange({userTaskDurationTime: {$set: value}}, true)}
        >
          <Select.Option value="idle">{t('report.config.userTaskDuration.idle')}</Select.Option>
          <Select.Option value="work">{t('report.config.userTaskDuration.work')}</Select.Option>
          <Select.Option value="total">{t('report.config.userTaskDuration.total')}</Select.Option>
        </Select>
      </fieldset>
    );
  }
  return null;
}
