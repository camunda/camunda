/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Select} from 'components';
import {t} from 'translation';

export default function DateVariableUnit({
  report: {
    data: {configuration, groupBy},
  },
  onChange,
}) {
  if (groupBy?.type === 'variable' && groupBy.value?.type === 'Date') {
    return (
      <fieldset className="DateVariableUnit">
        <legend>{t('report.config.DateGrouping')}</legend>
        <Select
          value={configuration.groupByDateVariableUnit}
          onChange={(value) => onChange({groupByDateVariableUnit: {$set: value}}, true)}
        >
          <Select.Option value="automatic">{t('report.groupBy.automatic')}</Select.Option>
          <Select.Option value="hour">{t('report.groupBy.hour')}</Select.Option>
          <Select.Option value="day">{t('report.groupBy.day')}</Select.Option>
          <Select.Option value="week">{t('report.groupBy.week')}</Select.Option>
          <Select.Option value="month">{t('report.groupBy.month')}</Select.Option>
          <Select.Option value="year">{t('report.groupBy.year')}</Select.Option>
        </Select>
      </fieldset>
    );
  }
  return null;
}
