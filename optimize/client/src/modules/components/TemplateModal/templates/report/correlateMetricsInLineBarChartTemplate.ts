/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'translation';

import correlateMetricsInLineBarChart from './images/correlateMetricsInLineBarChart.png';

export function correlateMetricsInLineBarChartTemplate() {
  return {
    name: 'correlateMetricsInLineBarChart',
    img: correlateMetricsInLineBarChart,
    disabled: (definitions: unknown[]) => definitions.length === 0,
    config: {
      configuration: {
        pointMarkers: true,
        xLabel: t('report.groupBy.endDate'),
        yLabel: t('report.view.pi') + ' ' + t('report.view.count'),
      },
      view: {
        entity: 'processInstance',
        properties: ['frequency', 'duration'],
      },
      groupBy: {
        type: 'endDate',
        value: {
          unit: 'month',
        },
      },
      visualization: 'barLine',
    },
  };
}
