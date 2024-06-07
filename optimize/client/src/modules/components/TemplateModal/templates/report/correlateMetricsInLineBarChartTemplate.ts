/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
