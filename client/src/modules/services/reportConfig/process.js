/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const view = [
  {label: 'Raw Data', group: 'raw', data: {property: 'rawData', entity: null}},
  {
    label: 'Process Instance',
    group: 'pi',
    options: [
      {label: 'Count', data: {property: 'frequency', entity: 'processInstance'}},
      {label: 'Duration', data: {property: 'duration', entity: 'processInstance'}}
    ]
  },
  {
    label: 'Flow Node',
    group: 'fn',
    options: [
      {label: 'Count', data: {property: 'frequency', entity: 'flowNode'}},
      {label: 'Duration', data: {property: 'duration', entity: 'flowNode'}}
    ]
  },
  {
    label: 'User Task Duration',
    group: 'fn',
    options: [
      {label: 'Idle', data: {property: 'idleDuration', entity: 'userTask'}},
      {label: 'Work', data: {property: 'workDuration', entity: 'userTask'}},
      {label: 'Total', data: {property: 'duration', entity: 'userTask'}}
    ]
  }
];

export const groupBy = [
  {label: 'None', group: 'none', data: {type: 'none', value: null}},
  {label: 'Flow Nodes', group: 'fn', data: {type: 'flowNodes', value: null}},
  {
    label: 'Start Date of Process Instance',
    group: 'date',
    options: [
      {label: 'Automatic', data: {type: 'startDate', value: {unit: 'automatic'}}},
      {label: 'Year', data: {type: 'startDate', value: {unit: 'year'}}},
      {label: 'Month', data: {type: 'startDate', value: {unit: 'month'}}},
      {label: 'Week', data: {type: 'startDate', value: {unit: 'week'}}},
      {label: 'Day', data: {type: 'startDate', value: {unit: 'day'}}},
      {label: 'Hour', data: {type: 'startDate', value: {unit: 'hour'}}}
    ]
  },
  {label: 'Variable', group: 'variable', options: 'variable'}
];

export const visualization = [
  {label: 'Number', group: 'number', data: 'number'},
  {label: 'Table', group: 'table', data: 'table'},
  {label: 'Bar Chart', group: 'chart', data: 'bar'},
  {label: 'Line Chart', group: 'chart', data: 'line'},
  {label: 'Pie Chart', group: 'chart', data: 'pie'},
  {label: 'Heatmap', group: 'heat', data: 'heat'}
];

export const combinations = {
  raw: {
    none: ['table']
  },
  pi: {
    none: ['number'],
    date: ['table', 'chart'],
    variable: ['table', 'chart']
  },
  fn: {
    fn: ['table', 'chart', 'heat']
  }
};
