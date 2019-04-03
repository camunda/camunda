/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import ChartRenderer from './ChartRenderer';
import createDefaultChartConfig from './defaultChart';
import createCombinedChartConfig from './combinedChart';
import createTargetLineConfig from './targetLineChart';
import ReportBlankSlate from '../../ReportBlankSlate';

export default function Chart(props) {
  const {
    report: {
      combined,
      result,
      data: {configuration, visualization, view}
    },
    errorMessage
  } = props;

  if (!result || typeof result !== 'object') {
    return <ReportBlankSlate errorMessage={errorMessage} />;
  }

  const reportView = view || Object.values(result)[0].data.view;
  const targetValueType = reportView.property === 'frequency' ? 'countChart' : 'durationChart';
  const targetValue =
    configuration.targetValue.active && configuration.targetValue[targetValueType];

  let createConfig;
  if (targetValue && visualization === 'line') {
    createConfig = createTargetLineConfig;
  } else if (combined) {
    createConfig = createCombinedChartConfig;
  } else {
    createConfig = createDefaultChartConfig;
  }

  return <ChartRenderer config={createConfig({...props, targetValue})} />;
}
