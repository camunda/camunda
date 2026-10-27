/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'translation';

import compareProcessesAndVersionsInABarChart from './images/compareProcessesAndVersionsInABarChart.png';

export function compareProcessesAndVersionsInABarChartTemplate() {
  return {
    name: 'compareProcessesAndVersionsInABarChart',
    img: compareProcessesAndVersionsInABarChart,
    disabled: (definitions: unknown[]) => definitions.length < 2,
    config: {
      configuration: {
        color: '#00d0a3',
        sorting: {
          by: 'value',
          order: 'desc',
        },
        xLabel: t('common.process.label'),
        yLabel: t('report.view.pi') + ' ' + t('report.view.count'),
      },
      view: {
        entity: 'processInstance',
        properties: ['frequency'],
      },
      distributedBy: {
        type: 'process',
        value: null,
      },
      groupBy: {
        type: 'none',
        value: null,
      },
      visualization: 'bar',
    },
  };
}
