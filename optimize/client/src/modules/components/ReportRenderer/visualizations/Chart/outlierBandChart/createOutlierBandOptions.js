/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'translation';

import createDefaultChartOptions from '../defaultChart/createDefaultChartOptions';

// Reuses the standard line-chart option machinery (axes, ticks, tooltips) by treating the report
// as a line chart, then layers on the band-specific tweaks: a fixed Y-axis title and a reversed
// legend so it reads p95 → p50 → p5 (top band first), matching the visual stack order.
export default function createOutlierBandOptions(props) {
  const lineProps = {
    ...props,
    report: {
      ...props.report,
      data: {...props.report.data, visualization: 'line'},
    },
  };

  const options = createDefaultChartOptions(lineProps);

  const measuresAxis = options.scales?.['axis-0'];
  if (measuresAxis) {
    measuresAxis.title = {
      ...measuresAxis.title,
      display: true,
      text: t('report.outlierBand.yAxis'),
    };
  }

  options.plugins.legend = {
    ...options.plugins.legend,
    display: true,
    reverse: true,
  };

  return options;
}
