/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
