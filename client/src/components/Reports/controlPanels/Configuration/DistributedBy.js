/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Select} from 'components';
import {t} from 'translation';

export default function DistributedBy({
  report: {
    data: {configuration, view, groupBy, visualization},
  },
  onChange,
}) {
  if (canDistributeData(view, groupBy)) {
    return (
      <fieldset className="DistributedBy">
        <legend>{t('report.config.userTaskDistributedBy')}</legend>
        <Select
          value={configuration.distributedBy.type}
          onChange={(value) => {
            if (value !== 'none' && !['line', 'table'].includes(visualization)) {
              onChange(
                {
                  visualization: {$set: 'bar'},
                  configuration: {distributedBy: {type: {$set: value}}},
                },
                true
              );
            } else {
              onChange({configuration: {distributedBy: {type: {$set: value}}}}, true);
            }
          }}
        >
          <Select.Option value="none">{t('common.nothing')}</Select.Option>
          {getOptionsFor(view.entity, groupBy.type)}
        </Select>
      </fieldset>
    );
  }
  return null;
}

function canDistributeData(view, groupBy) {
  if (!view || !groupBy) {
    return false;
  }
  if (view.entity === 'userTask') {
    return true;
  }
  if (view.entity === 'flowNode' && (groupBy.type === 'startDate' || groupBy.type === 'endDate')) {
    return true;
  }
}

function getOptionsFor(view, groupBy) {
  const options = [];

  if (view === 'userTask') {
    if (['userTasks', 'startDate', 'endDate'].includes(groupBy)) {
      options.push(
        <Select.Option key="assignee" value="assignee">
          {t('report.groupBy.userAssignee')}
        </Select.Option>,
        <Select.Option key="candidateGroup" value="candidateGroup">
          {t('report.groupBy.userGroup')}
        </Select.Option>
      );
    }

    if (groupBy !== 'userTasks') {
      options.push(
        <Select.Option key="userTask" value="userTask">
          {t('report.view.userTask')}
        </Select.Option>
      );
    }
  }

  if (view === 'flowNode') {
    if (groupBy === 'startDate' || groupBy === 'endDate') {
      options.push(
        <Select.Option key="flowNode" value="flowNode">
          {t('report.view.fn')}
        </Select.Option>
      );
    }
  }

  return options;
}
