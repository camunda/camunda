/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import createDefaultChartData from './createDefaultChartData';
import createDefaultChartOptions from './createDefaultChartOptions';
import createPlugins from '../createPlugins';

export default function createDefaultChartConfig(props) {
  const {
    data: {visualization},
  } = props.report;

  return {
    type: visualization === 'barLine' ? 'bar' : visualization,
    data: createDefaultChartData(props),
    options: createDefaultChartOptions(props),
    plugins: createPlugins(props),
  };
}
