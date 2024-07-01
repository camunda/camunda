/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {t} from 'translation';

export const view = [
  {
    key: 'rawData',
    label: () => t('report.view.rawData'),
    visible: () => true,
    enabled: () => true,
    matcher: ({view}) => view?.properties[0] === 'rawData',
    payload: () => ({view: {properties: ['rawData']}}),
  },
  {
    key: 'evaluationCount',
    label: () => t('report.view.evaluationCount'),
    visible: () => true,
    enabled: () => true,
    matcher: ({view}) => view?.properties[0] === 'frequency',
    payload: () => ({view: {properties: ['frequency']}}),
  },
];

export const group = [
  {
    key: 'none',
    label: () => t('report.groupBy.none'),
    visible: () => true,
    enabled: () => true,
    matcher: ({groupBy}) => groupBy?.type === 'none',
    payload: () => ({
      groupBy: {type: 'none', value: null},
    }),
    priority: 1,
  },
  {
    key: 'rules',
    label: () => t('report.groupBy.rules'),
    visible: () => true,
    enabled: ({view}) => view.properties[0] === 'frequency',
    matcher: ({groupBy}) => groupBy?.type === 'matchedRule',
    payload: () => ({groupBy: {type: 'matchedRule', value: null}}),
    priority: 2,
  },
  {
    key: 'evaluationDate',
    label: () => t('report.groupBy.evaluationDate'),
    visible: () => true,
    enabled: ({view}) => view.properties[0] === 'frequency',
    matcher: ({groupBy}) => groupBy?.type === 'evaluationDateTime',
    payload: () => ({groupBy: {type: 'evaluationDateTime'}}),
    priority: 3,
  },
  {
    key: 'inputVariable',
    label: () => t('report.groupBy.inputVariable'),
    visible: () => true,
    enabled: ({view}) => view.properties[0] === 'frequency',
    matcher: ({groupBy}) => groupBy?.type === 'inputVariable',
    payload: () => ({groupBy: {type: 'inputVariable'}}),
    priority: 4,
  },
  {
    key: 'outputVariable',
    label: () => t('report.groupBy.outputVariable'),
    visible: () => true,
    enabled: ({view}) => view.properties[0] === 'frequency',
    matcher: ({groupBy}) => groupBy?.type === 'outputVariable',
    payload: () => ({groupBy: {type: 'outputVariable'}}),
    priority: 5,
  },
];

export const visualization = [
  {
    key: 'number',
    label: () => t('report.visualization.number'),
    visible: () => true,
    enabled: ({groupBy, view}) => groupBy.type === 'none' && view.properties[0] === 'frequency',
    matcher: ({visualization}) => visualization === 'number',
    payload: () => ({visualization: 'number'}),
    priority: 2,
  },
  {
    key: 'table',
    label: () => t('report.visualization.table'),
    visible: () => true,
    enabled: ({groupBy, view}) => groupBy.type !== 'none' || view.properties[0] === 'rawData',
    matcher: ({visualization}) => visualization === 'table',
    payload: () => ({visualization: 'table'}),
    priority: 1,
  },
  {
    key: 'barChart',
    label: () => t('report.visualization.bar'),
    visible: () => true,
    enabled: ({groupBy}) =>
      ['evaluationDateTime', 'inputVariable', 'outputVariable'].includes(groupBy.type),
    matcher: ({visualization}) => visualization === 'bar',
    payload: () => ({visualization: 'bar'}),
    priority: 3,
  },
  {
    key: 'lineChart',
    label: () => t('report.visualization.line'),
    visible: () => true,
    enabled: ({groupBy}) =>
      ['evaluationDateTime', 'inputVariable', 'outputVariable'].includes(groupBy.type),
    matcher: ({visualization}) => visualization === 'line',
    payload: () => ({visualization: 'line'}),
    priority: 4,
  },
  {
    key: 'pieChart',
    label: () => t('report.visualization.pie'),
    visible: () => true,
    enabled: ({groupBy}) =>
      ['evaluationDateTime', 'inputVariable', 'outputVariable'].includes(groupBy.type),
    matcher: ({visualization}) => visualization === 'pie',
    payload: () => ({visualization: 'pie'}),
    priority: 5,
  },
];
