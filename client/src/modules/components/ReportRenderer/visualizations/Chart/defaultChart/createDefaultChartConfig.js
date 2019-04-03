/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import createDefaultChartData from './createDefaultChartData';
import createDefaultChartOptions from './createDefaultChartOptions';
import createPlugins from '../createPlugins';

export default function createDefaultChartConfig(props) {
  const {
    data: {visualization}
  } = props.report;

  return {
    type: visualization,
    data: createDefaultChartData(props),
    options: createDefaultChartOptions(props),
    plugins: createPlugins(props)
  };
}
