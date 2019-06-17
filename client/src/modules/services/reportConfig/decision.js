/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const view = [
  {label: 'Raw Data', group: 'raw', data: {property: 'rawData'}},
  {label: 'Evaluation Count', group: 'count', data: {property: 'frequency'}}
];

export const groupBy = [
  {label: 'None', group: 'none', data: {type: 'none', value: null}},
  {label: 'Rules', group: 'rule', data: {type: 'matchedRule', value: null}},
  {
    label: 'Evaluation Date',
    group: 'date',
    options: [
      {label: 'Automatic', data: {type: 'evaluationDateTime', value: {unit: 'automatic'}}},
      {label: 'Year', data: {type: 'evaluationDateTime', value: {unit: 'year'}}},
      {label: 'Month', data: {type: 'evaluationDateTime', value: {unit: 'month'}}},
      {label: 'Week', data: {type: 'evaluationDateTime', value: {unit: 'week'}}},
      {label: 'Day', data: {type: 'evaluationDateTime', value: {unit: 'day'}}},
      {label: 'Hour', data: {type: 'evaluationDateTime', value: {unit: 'hour'}}}
    ]
  },
  {label: 'Input Variable', group: 'variable', options: 'inputVariable'},
  {label: 'Output Variable', group: 'variable', options: 'outputVariable'}
];

export const visualization = [
  {label: 'Number', group: 'number', data: 'number'},
  {label: 'Table', group: 'table', data: 'table'},
  {label: 'Bar Chart', group: 'chart', data: 'bar'},
  {label: 'Line Chart', group: 'chart', data: 'line'},
  {label: 'Pie Chart', group: 'chart', data: 'pie'}
];

export const combinations = {
  raw: {
    none: ['table']
  },
  count: {
    none: ['number'],
    rule: ['table'],
    date: ['table', 'chart'],
    variable: ['table', 'chart']
  }
};
