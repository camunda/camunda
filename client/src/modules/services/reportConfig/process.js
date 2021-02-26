/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const view = [
  {key: 'rawData', group: 'raw', data: {properties: ['rawData'], entity: null}},
  {
    key: 'pi',
    options: [
      {
        key: 'pi_count',
        group: 'pi_count',
        data: {properties: ['frequency'], entity: 'processInstance'},
      },
      {
        key: 'pi_duration',
        group: 'pi_duration',
        data: {properties: ['duration'], entity: 'processInstance'},
      },
      {
        key: 'pi_multi',
        group: 'pi_multi',
        data: {properties: ['frequency', 'duration'], entity: 'processInstance'},
      },
    ],
  },
  {
    key: 'in',
    options: [
      {
        key: 'in_count',
        group: 'in_count',
        data: {properties: ['frequency'], entity: 'incident'},
      },
      {
        key: 'in_resolutionDuration',
        group: 'in_resolutionDuration',
        data: {properties: ['duration'], entity: 'incident'},
      },
      {
        key: 'in_multi',
        group: 'in_multi',
        data: {properties: ['frequency', 'duration'], entity: 'incident'},
      },
    ],
  },
  {
    key: 'fn',
    options: [
      {
        key: 'fn_count',
        group: 'fn_count',
        data: {properties: ['frequency'], entity: 'flowNode'},
      },
      {
        key: 'fn_duration',
        group: 'fn_duration',
        data: {properties: ['duration'], entity: 'flowNode'},
      },
      {
        key: 'fn_multi',
        group: 'fn_multi',
        data: {properties: ['frequency', 'duration'], entity: 'flowNode'},
      },
    ],
  },
  {
    key: 'userTask',
    options: [
      {
        key: 'userTask_count',
        group: 'userTask_count',
        data: {properties: ['frequency'], entity: 'userTask'},
      },
      {
        key: 'userTask_duration',
        group: 'userTask_duration',
        data: {properties: ['duration'], entity: 'userTask'},
      },
      {
        key: 'userTask_multi',
        group: 'userTask_multi',
        data: {properties: ['frequency', 'duration'], entity: 'userTask'},
      },
    ],
  },
  {key: 'variable', group: 'variable', options: 'variable'},
];

export const groupBy = [
  {key: 'none', group: 'none', data: {type: 'none', value: null}},
  {key: 'flowNodes', group: 'fn', data: {type: 'flowNodes', value: null}},
  {key: 'userTasks', group: 'task', data: {type: 'userTasks', value: null}},
  {key: 'duration', group: 'duration', data: {type: 'duration', value: null}},
  {
    key: 'startDate',
    group: 'date',
    options: [
      {
        key: 'startDate_automatic',

        data: {type: 'startDate', value: {unit: 'automatic'}},
      },
      {key: 'startDate_year', data: {type: 'startDate', value: {unit: 'year'}}},
      {key: 'startDate_month', data: {type: 'startDate', value: {unit: 'month'}}},
      {key: 'startDate_week', data: {type: 'startDate', value: {unit: 'week'}}},
      {key: 'startDate_day', data: {type: 'startDate', value: {unit: 'day'}}},
      {key: 'startDate_hour', data: {type: 'startDate', value: {unit: 'hour'}}},
    ],
  },
  {
    key: 'runningDate',
    group: 'runningDate',
    options: [
      {
        key: 'runningDate_automatic',

        data: {type: 'runningDate', value: {unit: 'automatic'}},
      },
      {key: 'runningDate_year', data: {type: 'runningDate', value: {unit: 'year'}}},
      {key: 'runningDate_month', data: {type: 'runningDate', value: {unit: 'month'}}},
      {key: 'runningDate_week', data: {type: 'runningDate', value: {unit: 'week'}}},
      {key: 'runningDate_day', data: {type: 'runningDate', value: {unit: 'day'}}},
      {key: 'runningDate_hour', data: {type: 'runningDate', value: {unit: 'hour'}}},
    ],
  },
  {
    key: 'endDate',
    group: 'date',
    options: [
      {
        key: 'endDate_automatic',
        data: {type: 'endDate', value: {unit: 'automatic'}},
      },
      {key: 'endDate_year', data: {type: 'endDate', value: {unit: 'year'}}},
      {key: 'endDate_month', data: {type: 'endDate', value: {unit: 'month'}}},
      {key: 'endDate_week', data: {type: 'endDate', value: {unit: 'week'}}},
      {key: 'endDate_day', data: {type: 'endDate', value: {unit: 'day'}}},
      {key: 'endDate_hour', data: {type: 'endDate', value: {unit: 'hour'}}},
    ],
  },
  {key: 'variable', group: 'variable', options: 'variable'},
  {key: 'userAssignee', group: 'user', data: {type: 'assignee', value: null}},
  {
    key: 'userGroup',
    group: 'user',
    data: {type: 'candidateGroup', value: null},
  },
];

export const visualization = [
  {key: 'number', group: 'number', data: 'number'},
  {key: 'table', group: 'table', data: 'table'},
  {key: 'bar', group: 'chart', data: 'bar'},
  {key: 'line', group: 'chart', data: 'line'},
  {key: 'pie', group: 'chart', data: 'pie'},
  {key: 'heat', group: 'heat', data: 'heat'},
];

export const combinations = {
  raw: {
    none: ['table'],
  },
  pi_duration: {
    none: ['number'],
    date: ['table', 'chart'],
    variable: ['table', 'chart'],
  },
  pi_count: {
    none: ['number'],
    date: ['table', 'chart'],
    runningDate: ['table', 'chart'],
    variable: ['table', 'chart'],
    duration: ['table', 'chart'],
  },
  pi_multi: {
    none: ['number'],
    date: ['table', 'chart'],
    variable: ['table', 'chart'],
  },
  in_resolutionDuration: {
    none: ['number'],
    fn: ['table', 'chart', 'heat'],
  },
  in_count: {
    none: ['number'],
    fn: ['table', 'chart', 'heat'],
  },
  in_multi: {
    none: ['number'],
    fn: ['table', 'chart'],
  },
  fn_duration: {
    fn: ['table', 'chart', 'heat'],
    date: ['table', 'chart'],
    variable: ['table', 'chart'],
  },
  fn_count: {
    fn: ['table', 'chart', 'heat'],
    date: ['table', 'chart'],
    duration: ['table', 'chart'],
    variable: ['table', 'chart'],
  },
  fn_multi: {
    fn: ['table', 'chart'],
    date: ['table', 'chart'],
    variable: ['table', 'chart'],
  },
  userTask_duration: {
    task: ['table', 'chart', 'heat'],
    date: ['table', 'chart'],
    user: ['table', 'chart'],
  },
  userTask_count: {
    task: ['table', 'chart', 'heat'],
    date: ['table', 'chart'],
    user: ['table', 'chart'],
    duration: ['table', 'chart'],
  },
  userTask_multi: {
    task: ['table', 'chart'],
    date: ['table', 'chart'],
    user: ['table', 'chart'],
  },
  variable: {
    none: ['number'],
  },
};
