/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {t} from 'translation';

import {getDefaultSorting} from './reportConfig';

export const view = [
  {
    key: 'rawData',
    label: () => t('report.view.rawData'),
    visible: () => true,
    enabled: () => true,
    matcher: ({view}) => view?.properties[0] === 'rawData',
    payload: () => ({view: {entity: null, properties: ['rawData']}}),
  },
  {
    key: 'processInstance',
    label: () => t('report.view.pi'),
    visible: () => true,
    enabled: () => true,
    matcher: ({view}) => view?.entity === 'processInstance',
    payload: ({view}) => {
      if (!view || view.properties[0] === 'rawData' || typeof view.properties[0] === 'object') {
        return {view: {entity: 'processInstance', properties: ['frequency']}};
      }
      return {view: {entity: 'processInstance', properties: view.properties}};
    },
  },
  {
    key: 'incident',
    label: () => t('report.view.in'),
    visible: () => true,
    enabled: () => true,
    matcher: ({view}) => view?.entity === 'incident',
    payload: ({view}) => {
      if (!view || view.properties[0] === 'rawData' || typeof view.properties[0] === 'object') {
        return {view: {entity: 'incident', properties: ['frequency']}};
      }
      return {view: {entity: 'incident', properties: view.properties}};
    },
  },
  {
    key: 'flowNode',
    label: () => t('report.view.fn'),
    visible: () => true,
    enabled: () => true,
    matcher: ({view}) => view?.entity === 'flowNode',
    payload: ({view}) => {
      if (!view || view.properties[0] === 'rawData' || typeof view.properties[0] === 'object') {
        return {view: {entity: 'flowNode', properties: ['frequency']}};
      }
      return {view: {entity: 'flowNode', properties: view.properties}};
    },
  },
  {
    key: 'userTask',
    label: () => t('report.view.userTask'),
    visible: () => true,
    enabled: () => true,
    matcher: ({view}) => view?.entity === 'userTask',
    payload: ({view}) => {
      if (!view || view.properties[0] === 'rawData' || typeof view.properties[0] === 'object') {
        return {view: {entity: 'userTask', properties: ['frequency']}};
      }
      return {view: {entity: 'userTask', properties: view.properties}};
    },
  },
  {
    key: 'variable',
    label: () => t('report.view.variable'),
    visible: () => true,
    enabled: () => true,
    matcher: ({view}) => view?.entity === 'variable',
    payload: () => {
      return {view: {entity: 'variable'}};
    },
  },
];

export const group = [
  {
    key: 'none',
    label: () => t('report.groupBy.none'),
    visible: () => true,
    enabled: ({view}) => [null, 'processInstance', 'incident', 'variable'].includes(view.entity),
    matcher: ({groupBy, distributedBy}) =>
      groupBy?.type === 'none' && distributedBy?.type === 'none',
    payload: () => ({
      groupBy: {type: 'none', value: null},
      distributedBy: {type: 'none', value: null},
    }),
    priority: 3,
  },
  {
    key: 'flowNodes',
    label: () => t('report.groupBy.flowNodes'),
    visible: () => true,
    enabled: ({view}) => ['flowNode', 'incident'].includes(view.entity),
    matcher: ({groupBy}) => groupBy?.type === 'flowNodes',
    payload: () => ({groupBy: {type: 'flowNodes', value: null}}),
    priority: 1,
  },
  {
    key: 'userTasks',
    label: () => t('report.groupBy.userTasks'),
    visible: () => true,
    enabled: ({view}) => view.entity === 'userTask',
    matcher: ({groupBy}) => groupBy?.type === 'userTasks',
    payload: () => ({groupBy: {type: 'userTasks', value: null}}),
    priority: 2,
  },
  {
    key: 'duration',
    label: () => t('report.groupBy.duration'),
    visible: () => true,
    enabled: ({view}) =>
      ['processInstance', 'flowNode', 'userTask'].includes(view.entity) &&
      view.properties.length === 1 &&
      view.properties[0] === 'frequency',
    matcher: ({groupBy}) => groupBy?.type === 'duration',
    payload: () => ({groupBy: {type: 'duration', value: null}}),
    priority: 6,
  },
  {
    key: 'startDate',
    label: () => t('report.groupBy.startDate'),
    visible: () => true,
    enabled: ({view}) =>
      ['processInstance', 'flowNode', 'userTask'].includes(view.entity) &&
      !view.properties.includes('percentage'),
    matcher: ({groupBy}) => groupBy?.type === 'startDate',
    payload: () => ({groupBy: {type: 'startDate', value: null}}),
    priority: 4,
  },
  {
    key: 'runningDate',
    label: () => t('report.groupBy.runningDate'),
    visible: () => true,
    enabled: ({view}) =>
      view.entity === 'processInstance' &&
      view.properties.length === 1 &&
      view.properties[0] === 'frequency',
    matcher: ({groupBy}) => groupBy?.type === 'runningDate',
    payload: () => ({groupBy: {type: 'runningDate', value: null}}),
    priority: 7,
  },
  {
    key: 'endDate',
    label: () => t('report.groupBy.endDate'),
    visible: () => true,
    enabled: ({view}) =>
      ['processInstance', 'flowNode', 'userTask'].includes(view.entity) &&
      !view.properties.includes('percentage'),
    matcher: ({groupBy}) => groupBy?.type === 'endDate',
    payload: () => ({groupBy: {type: 'endDate', value: null}}),
    priority: 5,
  },
  {
    key: 'variable',
    label: () => t('report.groupBy.variable'),
    visible: () => true,
    enabled: ({view}) =>
      ['processInstance', 'flowNode'].includes(view.entity) &&
      !view.properties.includes('percentage'),
    matcher: ({groupBy}) => groupBy?.type === 'variable',
    payload: () => ({groupBy: {type: 'variable', value: null}}),
    priority: 11,
  },
  {
    key: 'assignee',
    label: () => t('report.groupBy.userAssignee'),
    visible: () => true,
    enabled: ({view}) => view.entity === 'userTask',
    matcher: ({groupBy}) => groupBy?.type === 'assignee',
    payload: () => ({groupBy: {type: 'assignee', value: null}}),
    priority: 8,
  },
  {
    key: 'candidateGroup',
    label: () => t('report.groupBy.userGroup'),
    visible: () => true,
    enabled: ({view}) => view.entity === 'userTask',
    matcher: ({groupBy}) => groupBy?.type === 'candidateGroup',
    payload: () => ({groupBy: {type: 'candidateGroup', value: null}}),
    priority: 9,
  },
  {
    key: 'process',
    label: () => t('common.process.label'),
    visible: ({definitions, view}) =>
      definitions.length > 1 &&
      view.entity === 'processInstance' &&
      !view.properties.includes('percentage'),
    enabled: () => true,
    matcher: ({groupBy, distributedBy}) =>
      groupBy?.type === 'none' && distributedBy?.type === 'process',
    payload: () => ({
      groupBy: {type: 'none', value: null},
      distributedBy: {type: 'process', value: null},
    }),
    priority: 10,
  },
];

export const distribution = [
  {
    key: 'none',
    label: () => t('report.groupBy.none'),
    visible: () => true,
    enabled: () => true,
    matcher: ({groupBy, distributedBy}) => {
      if (groupBy?.type !== 'none') {
        return distributedBy?.type === 'none';
      } else {
        return ['none', 'process'].includes(distributedBy?.type);
      }
    },
    payload: () => ({distributedBy: {type: 'none', value: null}}),
    priority: 3,
  },
  {
    key: 'flowNodes',
    label: () => t('common.flowNode.label'),
    visible: ({view, groupBy}) =>
      view.entity === 'flowNode' &&
      ['startDate', 'endDate', 'duration', 'variable'].includes(groupBy.type),
    enabled: () => true,
    matcher: ({distributedBy}) => distributedBy.type === 'flowNode',
    payload: () => ({distributedBy: {type: 'flowNode', value: null}}),
    priority: 1,
  },
  {
    key: 'userTasks',
    label: () => t('common.userTask.label'),
    visible: ({view, groupBy}) => view.entity === 'userTask' && groupBy.type !== 'userTasks',
    enabled: () => true,
    matcher: ({distributedBy}) => distributedBy.type === 'userTask',
    payload: () => ({distributedBy: {type: 'userTask', value: null}}),
    priority: 2,
  },
  {
    key: 'startDate',
    label: () => t('report.groupBy.startDate'),
    visible: ({view, groupBy}) => view.entity === 'processInstance' && groupBy.type === 'variable',
    enabled: () => true,
    matcher: ({distributedBy}) => distributedBy.type === 'startDate',
    payload: () => ({distributedBy: {type: 'startDate', value: null}}),
    priority: 4,
  },
  {
    key: 'endDate',
    label: () => t('report.groupBy.endDate'),
    visible: ({view, groupBy}) => view.entity === 'processInstance' && groupBy.type === 'variable',
    enabled: () => true,
    matcher: ({distributedBy}) => distributedBy.type === 'endDate',
    payload: () => ({distributedBy: {type: 'endDate', value: null}}),
    priority: 5,
  },
  {
    key: 'variable',
    label: () => t('report.groupBy.variable'),
    visible: ({view, groupBy}) =>
      view.entity === 'processInstance' && ['startDate', 'endDate'].includes(groupBy.type),
    enabled: () => true,
    matcher: ({distributedBy}) => distributedBy.type === 'variable',
    payload: () => ({distributedBy: {type: 'variable', value: null}}),
    priority: 6,
  },
  {
    key: 'assignee',
    label: () => t('report.groupBy.userAssignee'),
    visible: ({view, groupBy}) =>
      view.entity === 'userTask' && ['userTasks', 'startDate', 'endDate'].includes(groupBy.type),
    enabled: () => true,
    matcher: ({distributedBy}) => distributedBy.type === 'assignee',
    payload: () => ({distributedBy: {type: 'assignee', value: null}}),
    priority: 7,
  },
  {
    key: 'candidateGroup',
    label: () => t('report.groupBy.userGroup'),
    visible: ({view, groupBy}) =>
      view.entity === 'userTask' && ['userTasks', 'startDate', 'endDate'].includes(groupBy.type),
    enabled: () => true,
    matcher: ({distributedBy}) => distributedBy.type === 'candidateGroup',
    payload: () => ({distributedBy: {type: 'candidateGroup', value: null}}),
    priority: 8,
  },
  {
    key: 'process',
    label: () => t('common.process.label'),
    visible: ({definitions, view, groupBy}) =>
      definitions.length > 1 &&
      !['incident', 'variable', null].includes(view.entity) &&
      groupBy.type !== 'none' &&
      !view.properties.includes('percentage'),
    enabled: () => true,
    matcher: ({groupBy, distributedBy}) =>
      groupBy?.type !== 'none' && distributedBy.type === 'process',
    payload: () => ({distributedBy: {type: 'process', value: null}}),
    priority: 9,
  },
];

export const visualization = [
  {
    key: 'number',
    label: () => t('report.visualization.number'),
    visible: () => true,
    enabled: ({groupBy, distributedBy, view}) =>
      (groupBy.type === 'none' && distributedBy.type === 'none' && view.entity !== null) ||
      view.entity === 'variable',
    matcher: ({visualization}) => visualization === 'number',
    payload: () => ({visualization: 'number'}),
    priority: 2,
  },
  {
    key: 'table',
    label: () => t('report.visualization.table'),
    visible: () => true,
    enabled: ({groupBy, distributedBy, view}) =>
      groupBy.type !== 'none' || distributedBy.type !== 'none' || view.entity === null,
    matcher: ({visualization}) => visualization === 'table',
    payload: () => ({visualization: 'table'}),
    priority: 7,
  },
  {
    key: 'barChart',
    label: () => t('report.visualization.bar'),
    visible: () => true,
    enabled: ({groupBy, distributedBy}) => groupBy.type !== 'none' || distributedBy.type !== 'none',
    matcher: ({visualization}) => visualization === 'bar',
    payload: () => ({visualization: 'bar'}),
    priority: 3,
  },
  {
    key: 'lineChart',
    label: () => t('report.visualization.line'),
    visible: () => true,
    enabled: ({groupBy, distributedBy}) => groupBy.type !== 'none' || distributedBy.type !== 'none',
    matcher: ({visualization}) => visualization === 'line',
    payload: () => ({visualization: 'line'}),
    priority: 4,
  },
  {
    key: 'pieChart',
    label: () => t('report.visualization.pie'),
    visible: () => true,
    enabled: ({groupBy, distributedBy}) => {
      if (distributedBy.type !== 'none' && groupBy.type !== 'none') {
        // pie charts generally do not support distributed reports
        return false;
      }
      return groupBy.type !== 'none' || distributedBy.type !== 'none';
    },
    matcher: ({visualization}) => visualization === 'pie',
    payload: () => ({visualization: 'pie'}),
    priority: 5,
  },
  {
    key: 'comboChart',
    label: () => t('report.visualization.barLine'),
    visible: () => true,
    enabled: ({view, groupBy, distributedBy}) =>
      view.properties.length > 1 && (groupBy.type !== 'none' || distributedBy.type !== 'none'),
    matcher: ({visualization}) => visualization === 'barLine',
    payload: () => ({visualization: 'barLine'}),
    priority: 6,
  },
  {
    key: 'heatmap',
    label: () => t('report.visualization.heat'),
    visible: () => true,
    enabled: ({definitions, groupBy, distributedBy}) => {
      if (definitions.length > 1 || distributedBy.type !== 'none') {
        return false;
      }
      return ['flowNodes', 'userTasks'].includes(groupBy.type);
    },
    matcher: ({visualization}) => visualization === 'heat',
    payload: () => ({visualization: 'heat'}),
    priority: 1,
  },
];

export const sortingOrder = [
  {
    key: 'asc',
    label: () => t('report.sorting.order.asc'),
    visible: ({visualization, groupBy, distributedBy}) =>
      ['bar', 'barLine', 'line'].includes(visualization) &&
      groupBy?.type.toLowerCase().includes('date') &&
      !groupBy?.type.toLowerCase().includes('candidate') &&
      distributedBy.type === 'none',
    enabled: () => true,
    payload: (report) => {
      const {configuration} = report;
      const {by: defaultBy} = getDefaultSorting({reportType: 'process', data: report});
      return {
        configuration: {
          ...configuration,
          sorting: {by: configuration.sorting?.by || defaultBy, order: 'asc'},
        },
      };
    },
    priority: 9,
  },
  {
    key: 'desc',
    label: () => t('report.sorting.order.desc'),
    visible: ({visualization, groupBy, distributedBy}) =>
      ['bar', 'barLine', 'line'].includes(visualization) &&
      ['startDate', 'endDate', 'runningDate', 'evaluationDate'].includes(groupBy?.type) &&
      distributedBy.type === 'none',
    enabled: () => true,
    payload: (report) => {
      const {configuration} = report;
      const {by: defaultBy} = getDefaultSorting({reportType: 'process', data: report});
      return {
        configuration: {
          ...configuration,
          sorting: {by: configuration.sorting?.by || defaultBy, order: 'desc'},
        },
      };
    },
    priority: 9,
  },
];
