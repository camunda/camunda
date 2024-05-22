/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import monitorTargetAsKpi from './images/monitorTargetAsKpi.png';

export function monitorTargetAsKpiTemplate() {
  return {
    name: 'monitorTargetAsKpi',
    img: monitorTargetAsKpi,
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
          durationProgress: {
            target: {
              unit: 'hours',
              value: '12',
              isBelow: true,
            },
          },
          active: true,
          isKpi: true,
        },
      },
      view: {
        entity: 'processInstance',
        properties: ['duration'],
      },
      groupBy: {
        type: 'none',
        value: null,
      },
      visualization: 'number',
    },
  };
}
