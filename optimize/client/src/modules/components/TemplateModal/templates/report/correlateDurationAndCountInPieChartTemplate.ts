/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import correlateDurationAndCountInPieChart from './images/correlateDurationAndCountInPieChart.png';

export function correlateDurationAndCountInPieChartTemplate() {
  return {
    name: 'correlateDurationAndCountInPieChart',
    img: correlateDurationAndCountInPieChart,
    disabled: (definitions: unknown[]) => definitions.length === 0,
    config: {
      configuration: {
        showInstanceCount: true,
        sorting: {
          by: 'value',
          order: 'desc',
        },
      },
      filter: [
        {
          type: 'flowNodeStartDate',
          data: {
            type: 'relative',
            start: {
              value: 1,
              unit: 'years',
            },
            end: null,
            includeUndefined: false,
            excludeUndefined: false,
            flowNodeIds: null,
          },
          filterLevel: 'view',
          appliedTo: ['definition'],
        },
      ],
      view: {
        entity: 'userTask',
        properties: ['frequency', 'duration'],
      },
      groupBy: {
        type: 'userTasks',
        value: null,
      },
      visualization: 'pie',
      userTaskReport: true,
    },
  };
}
