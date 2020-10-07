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
    data: {view, groupBy, configuration},
  },
  onChange,
}) {
  const isUserTaskDateReport = groupBy?.type.includes('Date') && view?.entity === 'userTask';
  const isFlowNodeDurationReport =
    groupBy?.type === 'duration' && ['userTask', 'flowNode'].includes(view?.entity);
  const hasAllowedGroupby = ['userTasks', 'flowNodes', 'assignee', 'candidateGroup'].includes(
    groupBy?.type
  );

  const hasAllowedView = view?.entity !== 'incident';

  if (isUserTaskDateReport || isFlowNodeDurationReport || (hasAllowedGroupby && hasAllowedView)) {
    return (
      <fieldset className="NodeStatus">
        <legend>{t('report.config.nodeStatus.legend')}</legend>
        <Select
          value={configuration.flowNodeExecutionState}
          onChange={(value) => onChange({flowNodeExecutionState: {$set: value}}, true)}
        >
          <Select.Option value="running">{t('report.config.nodeStatus.running')}</Select.Option>
          <Select.Option value="completed">{t('report.config.nodeStatus.completed')}</Select.Option>
          <Select.Option value="canceled">{t('report.config.nodeStatus.canceled')}</Select.Option>
          <Select.Option value="all">{t('report.config.nodeStatus.all')}</Select.Option>
        </Select>
      </fieldset>
    );
  }
  return null;
}
