/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const view = [
  {key: 'rawData', label: 'Raw Data', group: 'raw', data: {property: 'rawData'}},
  {
    key: 'count_frequency',
    label: 'Evaluation Count',
    group: 'count',
    data: {property: 'frequency'}
  }
];

export const groupBy = [
  {key: 'none', label: 'None', group: 'none', data: {type: 'none', value: null}},
  {
    key: 'rule_matchedRule',
    label: 'Rules',
    group: 'rule',
    data: {type: 'matchedRule', value: null}
  },
  {
    label: 'Evaluation Date',
    group: 'date',
    options: [
      {
        key: 'date_automatic',
        label: 'Automatic',
        data: {type: 'evaluationDateTime', value: {unit: 'automatic'}}
      },
      {key: 'date_year', label: 'Year', data: {type: 'evaluationDateTime', value: {unit: 'year'}}},
      {
        key: 'date_month',
        label: 'Month',
        data: {type: 'evaluationDateTime', value: {unit: 'month'}}
      },
      {key: 'date_week', label: 'Week', data: {type: 'evaluationDateTime', value: {unit: 'week'}}},
      {key: 'date_day', label: 'Day', data: {type: 'evaluationDateTime', value: {unit: 'day'}}},
      {key: 'date_hour', label: 'Hour', data: {type: 'evaluationDateTime', value: {unit: 'hour'}}}
    ]
  },
  {label: 'Input Variable', group: 'variable', options: 'inputVariable'},
  {label: 'Output Variable', group: 'variable', options: 'outputVariable'}
];

export const visualization = [
  {key: 'number', label: 'Number', group: 'number', data: 'number'},
  {key: 'table', label: 'Table', group: 'table', data: 'table'},
  {key: 'bar', label: 'Bar Chart', group: 'chart', data: 'bar'},
  {key: 'line', label: 'Line Chart', group: 'chart', data: 'line'},
  {key: 'pie', label: 'Pie Chart', group: 'chart', data: 'pie'}
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
