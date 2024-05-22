/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
