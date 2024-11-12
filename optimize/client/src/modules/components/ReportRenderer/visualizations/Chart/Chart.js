/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import ChartRenderer from './ChartRenderer';
import createDefaultChartConfig from './defaultChart';
import createHyperChartConfig from './hyperChart';
import createTargetLineConfig from './targetLineChart';
import {themed} from 'theme';

export function Chart(props) {
  const {
    report: {
      hyper,
      result,
      data: {configuration, visualization, view},
    },
  } = props;

  const reportView = view || Object.values(result.data)[0].data.view;
  const targetValueType = ['frequency', 'percentage'].includes(reportView.properties[0])
    ? 'countChart'
    : 'durationChart';
  const targetValue =
    configuration.targetValue.active && configuration.targetValue[targetValueType];

  let createConfig;
  if (targetValue && visualization === 'line') {
    createConfig = createTargetLineConfig;
  } else if (hyper) {
    createConfig = createHyperChartConfig;
  } else {
    createConfig = createDefaultChartConfig;
  }

  return <ChartRenderer config={createConfig({...props, targetValue})} />;
}

export default themed(Chart);
