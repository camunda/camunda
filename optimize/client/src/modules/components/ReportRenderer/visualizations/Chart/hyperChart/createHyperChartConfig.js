/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import createHyperChartData from './createHyperChartData';
import createHyperChartOptions from './createHyperChartOptions';
import createPlugins from '../createPlugins';

export default function createHyperChartConfig(props) {
  const {visualization} = props.report.data;
  const chartVisualization = ['number', 'barLine'].includes(visualization) ? 'bar' : visualization;

  return {
    type: chartVisualization,
    data: createHyperChartData(props),
    options: createHyperChartOptions(props),
    plugins: createPlugins(props),
  };
}
