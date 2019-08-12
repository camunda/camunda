/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Select} from 'components';
import {t} from 'translation';

export default function NodeStatus({
  report: {
    data: {groupBy, configuration}
  },
  onChange
}) {
  if (groupBy && ['flowNodes', 'assignee', 'candidateGroup'].includes(groupBy.type)) {
    return (
      <fieldset className="NodeStatus">
        <legend>{t('report.config.nodeStatus.legend')}</legend>
        <Select
          value={configuration.flowNodeExecutionState}
          onChange={value => onChange({flowNodeExecutionState: {$set: value}}, true)}
        >
          <Select.Option value="running">{t('report.config.nodeStatus.running')}</Select.Option>
          <Select.Option value="completed">{t('report.config.nodeStatus.completed')}</Select.Option>
          <Select.Option value="all">{t('common.all')}</Select.Option>
        </Select>
      </fieldset>
    );
  }
  return null;
}
