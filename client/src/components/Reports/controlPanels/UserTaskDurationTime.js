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
    data: {configuration, view},
  },
  onChange,
}) {
  if (view && view.entity === 'userTask' && view.properties.includes('duration')) {
    return (
      <li className="UserTaskDurationTime">
        <span className="label">{t('report.config.userTaskDuration.legend')}</span>
        <Select
          className="ReportSelect"
          value={configuration.userTaskDurationTime}
          onChange={(value) =>
            onChange({configuration: {userTaskDurationTime: {$set: value}}}, true)
          }
        >
          <Select.Option value="idle">{t('report.config.userTaskDuration.idle')}</Select.Option>
          <Select.Option value="work">{t('report.config.userTaskDuration.work')}</Select.Option>
          <Select.Option value="total">{t('report.config.userTaskDuration.total')}</Select.Option>
        </Select>
      </li>
    );
  }
  return null;
}
