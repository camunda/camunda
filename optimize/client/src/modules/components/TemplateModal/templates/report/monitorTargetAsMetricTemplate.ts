/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
