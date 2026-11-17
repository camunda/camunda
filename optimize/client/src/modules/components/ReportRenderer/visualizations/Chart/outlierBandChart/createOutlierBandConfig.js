/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import createPlugins from '../createPlugins';

import createOutlierBandData from './createOutlierBandData';
import createOutlierBandOptions from './createOutlierBandOptions';

// Renders a report with three percentile measures (p5/p50/p95) as stacked shaded bands. Uses the
// native Chart.js line type plus the Filler plugin (already registered) — no custom controller
// needed, unlike targetLineChart.
export default function createOutlierBandConfig(props) {
  return {
    type: 'line',
    data: createOutlierBandData(props),
    options: createOutlierBandOptions(props),
    plugins: createPlugins(props),
  };
}
