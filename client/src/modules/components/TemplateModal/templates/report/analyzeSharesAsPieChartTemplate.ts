/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import analyzeSharesAsPieChart from './images/analyzeSharesAsPieChart.png';

export function analyzeSharesAsPieChartTemplate() {
  return {
    name: 'analyzeSharesAsPieChart',
    img: analyzeSharesAsPieChart,
    disabled: (definitions: unknown[]) => definitions.length === 0,
    config: {
      configuration: {
        alwaysShowAbsolute: true,
      },
      view: {
        entity: 'processInstance',
        properties: ['frequency'],
      },
      groupBy: {
        type: 'startDate',
        value: {
          unit: 'year',
        },
      },
      visualization: 'pie',
    },
  };
}
