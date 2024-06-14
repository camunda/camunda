/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {t} from 'translation';

import {KpiTemplate} from './types';

import throughputImg from './images/throughput.png';

export default function throughput(): KpiTemplate {
  return {
    name: t('report.kpiTemplates.throughput').toString(),
    description: t('report.kpiTemplates.throughput-description').toString(),
    img: throughputImg,
    config: {
      view: {entity: 'processInstance', properties: ['duration']},
      groupBy: {
        type: 'none',
        value: null,
      },
      visualization: 'number',
      configuration: {
        aggregationTypes: [{type: 'percentile', value: 90}],
      },
    },
    uiConfig: {
      filters: [
        {
          label: t('report.kpiTemplates.filters.nodeSelection').toString(),
          description: t('report.kpiTemplates.throughput-filter1').toString(),
          type: 'executedFlowNodes',
          data: {
            values: [],
          },
          filterLevel: 'view',
        },
        {
          label: t('report.kpiTemplates.filters.endDate').toString(),
          description: t('report.kpiTemplates.throughput-filter2').toString(),
          type: 'instanceEndDate',
          data: {},
          filterLevel: 'instance',
        },
      ],
    },
  };
}
