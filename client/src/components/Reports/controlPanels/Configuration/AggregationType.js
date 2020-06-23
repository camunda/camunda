/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Select} from 'components';
import {t} from 'translation';
import {isDurationReport} from 'services';

export default function AggregationType({report, onChange}) {
  const {data} = report;

  const isVariableReport = data?.view?.entity === 'variable';

  if (isDurationReport(report) || isVariableReport) {
    return (
      <fieldset className="AggregationType">
        <legend>{t('report.config.aggregation.legend')}</legend>
        <Select
          value={data.configuration.aggregationType}
          onChange={(value) => onChange({aggregationType: {$set: value}}, true)}
        >
          {isVariableReport && (
            <Select.Option value="sum">{t('report.config.aggregation.sum')}</Select.Option>
          )}
          <Select.Option value="min">{t('report.config.aggregation.minimum')}</Select.Option>
          <Select.Option value="avg">{t('report.config.aggregation.average')}</Select.Option>
          {!data.configuration.processPart && (
            <Select.Option value="median">{t('report.config.aggregation.median')}</Select.Option>
          )}
          <Select.Option value="max">{t('report.config.aggregation.maximum')}</Select.Option>
        </Select>
      </fieldset>
    );
  }
  return null;
}
