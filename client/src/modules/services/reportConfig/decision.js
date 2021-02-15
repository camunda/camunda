/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const view = [
  {key: 'rawData', group: 'raw', data: {properties: ['rawData']}},
  {
    key: 'evaluationCount',
    group: 'count',
    data: {properties: ['frequency']},
  },
];

export const groupBy = [
  {key: 'none', group: 'none', data: {type: 'none', value: null}},
  {
    key: 'rules',
    group: 'rule',
    data: {type: 'matchedRule', value: null},
  },
  {
    key: 'evaluationDate',
    group: 'date',
    options: [
      {
        key: 'evaluationDate_automatic',

        data: {type: 'evaluationDateTime', value: {unit: 'automatic'}},
      },
      {
        key: 'evaluationDate_year',

        data: {type: 'evaluationDateTime', value: {unit: 'year'}},
      },
      {
        key: 'evaluationDate_month',

        data: {type: 'evaluationDateTime', value: {unit: 'month'}},
      },
      {
        key: 'evaluationDate_week',

        data: {type: 'evaluationDateTime', value: {unit: 'week'}},
      },
      {
        key: 'evaluationDate_day',

        data: {type: 'evaluationDateTime', value: {unit: 'day'}},
      },
      {
        key: 'evaluationDate_hour',

        data: {type: 'evaluationDateTime', value: {unit: 'hour'}},
      },
    ],
  },
  {key: 'inputVariable', group: 'variable', options: 'inputVariable'},
  {key: 'outputVariable', group: 'variable', options: 'outputVariable'},
];

export const visualization = [
  {key: 'number', group: 'number', data: 'number'},
  {key: 'table', group: 'table', data: 'table'},
  {key: 'bar', group: 'chart', data: 'bar'},
  {key: 'line', group: 'chart', data: 'line'},
  {key: 'pie', group: 'chart', data: 'pie'},
];

export const combinations = {
  raw: {
    none: ['table'],
  },
  count: {
    none: ['number'],
    rule: ['table'],
    date: ['table', 'chart'],
    variable: ['table', 'chart'],
  },
};
