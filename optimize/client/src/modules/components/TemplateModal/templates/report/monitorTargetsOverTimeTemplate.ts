/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'translation';

import monitorTargetsOverTime from './images/monitorTargetsOverTime.png';

export function monitorTargetsOverTimeTemplate() {
  return {
    name: 'monitorTargetsOverTime',
    img: monitorTargetsOverTime,
    disabled: (definitions: unknown[]) => definitions.length === 0,
    config: {
      configuration: {
        color: '#FCCB00',
        aggregationTypes: [
          {
            type: 'avg',
            value: null,
          },
        ],
        targetValue: {
          active: true,
          durationChart: {
            unit: 'days',
            isBelow: false,
            value: '1',
          },
          isKpi: false,
        },
        xLabel: t('report.groupBy.endDate'),
        yLabel: t('report.view.pi') + ' ' + t('report.view.duration'),
      },
      view: {
        entity: 'processInstance',
        properties: ['duration'],
      },
      groupBy: {
        type: 'endDate',
        value: {
          unit: 'month',
        },
      },
      visualization: 'bar',
    },
  };
}
