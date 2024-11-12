/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
            operator: 'in',
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
