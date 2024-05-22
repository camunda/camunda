/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
