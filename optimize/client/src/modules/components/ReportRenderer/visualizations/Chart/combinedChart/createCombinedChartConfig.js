/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import createCombinedChartData from './createCombinedChartData';
import createCombinedChartOptions from './createCombinedChartOptions';
import createPlugins from '../createPlugins';

export default function createCombinedChartConfig(props) {
  const {visualization} = props.report.data;
  const chartVisualization = ['number', 'barLine'].includes(visualization) ? 'bar' : visualization;

  return {
    type: chartVisualization,
    data: createCombinedChartData(props),
    options: createCombinedChartOptions(props),
    plugins: createPlugins(props),
  };
}
