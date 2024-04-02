/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {t} from 'translation';

import {KpiTemplate} from './types';

import automationRateImg from './images/automationRate.png';

export default function automationRate(): KpiTemplate {
  return {
    name: t('report.kpiTemplates.automationRate').toString(),
    description: t('report.kpiTemplates.automationRate-description').toString(),
    img: automationRateImg,
    config: {
      view: {entity: 'processInstance', properties: ['percentage']},
      groupBy: {
        type: 'none',
        value: null,
      },
      visualization: 'number',
    },
    uiConfig: {
      filters: [
        {
          label: t('report.kpiTemplates.filters.nodeSelection').toString(),
          description: t('report.kpiTemplates.automationRate-filter1').toString(),
          type: 'executedFlowNodes',
          data: {
            values: [],
          },
          filterLevel: 'view',
        },
        {
          label: t('report.kpiTemplates.filters.endDate').toString(),
          description: t('report.kpiTemplates.automationRate-filter2').toString(),
          type: 'instanceEndDate',
          data: {},
          filterLevel: 'instance',
        },
      ],
    },
  };
}
