/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {ColorPicker} from 'components';

import CombinedReportRenderer from './CombinedReportRenderer';

export default function HyperReportRenderer({report, ...rest}) {
  const convertedReport = {
    ...report
  };

  const colors = ColorPicker.getColors(report.result.data[0].value.length);

  convertedReport.combined = true;
  convertedReport.data = {
    configuration: report.data.configuration,
    reports: report.result.data[0].value.map(({key}, i) => ({id: key, color: colors[i]})),
    visualization: report.data.visualization === 'table' ? 'table' : 'bar'
  };

  const newResultData = {};

  report.result.data[0].value.forEach(({key, label}) => {
    newResultData[key] = {
      combined: false,
      id: key,
      name: label,
      reportType: 'process',
      data: report.data,
      result: {
        ...report.result,
        type: report.data.view.property === 'duration' ? 'durationMap' : 'frequencyMap',
        data: report.result.data.map(entry => ({
          ...entry,
          value: entry.value.find(data => data.key === key).value
        }))
      }
    };
  });

  convertedReport.result = {
    type: null,
    data: newResultData
  };

  return <CombinedReportRenderer {...rest} report={convertedReport} />;
}
