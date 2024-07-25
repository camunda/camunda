/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
