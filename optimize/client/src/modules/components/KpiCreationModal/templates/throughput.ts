/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
            operator: 'in',
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
