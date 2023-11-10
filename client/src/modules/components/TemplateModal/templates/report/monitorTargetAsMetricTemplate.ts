/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import monitorTargetAsMetric from './images/monitorTargetAsMetric.png';

export function monitorTargetAsMetricTemplate() {
  return {
    name: 'monitorTargetAsMetric',
    img: monitorTargetAsMetric,
    disabled: (definitions: unknown[]) => definitions.length === 0,
    config: {
      configuration: {
        aggregationTypes: [
          {
            type: 'percentile',
            value: 75.0,
          },
        ],
        precision: 1,
        targetValue: {
          active: true,
          countProgress: {
            baseline: '1000',
            target: '2500',
            isBelow: false,
          },
          isKpi: false,
        },
      },
      view: {
        entity: 'processInstance',
        properties: ['frequency'],
      },
      groupBy: {
        type: 'none',
        value: null,
      },
      visualization: 'number',
    },
  };
}
