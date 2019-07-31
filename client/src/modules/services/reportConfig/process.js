/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const view = [
  {key: 'rawData', label: 'Raw Data', group: 'raw', data: {property: 'rawData', entity: null}},
  {
    key: 'pi',
    label: 'Process Instance',
    group: 'pi',
    options: [
      {
        key: 'pi_count',
        label: 'Count',
        data: {property: 'frequency', entity: 'processInstance'}
      },
      {
        key: 'pi_duration',
        label: 'Duration',
        data: {property: 'duration', entity: 'processInstance'}
      }
    ]
  },
  {
    key: 'fn',
    label: 'Flow Node',
    group: 'fn',
    options: [
      {
        key: 'fn_count',
        label: 'Count',
        data: {property: 'frequency', entity: 'flowNode'}
      },
      {key: 'fn_duration', label: 'Duration', data: {property: 'duration', entity: 'flowNode'}}
    ]
  },
  {
    key: 'userTask',
    label: 'User Task',
    group: 'userTask',
    options: [
      {
        key: 'userTask_count',
        label: 'Count',
        data: {property: 'frequency', entity: 'userTask'}
      },
      {
        key: 'userTask_duration',
        label: 'Duration',
        data: {property: 'duration', entity: 'userTask'}
      }
    ]
  }
];

export const groupBy = [
  {key: 'none', label: 'None', group: 'none', data: {type: 'none', value: null}},
  {key: 'flowNodes', label: 'Flow Nodes', group: 'fn', data: {type: 'flowNodes', value: null}},
  {
    key: 'startDate',
    label: 'Start Date of Process Instance',
    group: 'date',
    options: [
      {
        key: 'startDate_automatic',
        label: 'Automatic',
        data: {type: 'startDate', value: {unit: 'automatic'}}
      },
      {key: 'startDate_year', label: 'Year', data: {type: 'startDate', value: {unit: 'year'}}},
      {key: 'startDate_month', label: 'Month', data: {type: 'startDate', value: {unit: 'month'}}},
      {key: 'startDate_week', label: 'Week', data: {type: 'startDate', value: {unit: 'week'}}},
      {key: 'startDate_day', label: 'Day', data: {type: 'startDate', value: {unit: 'day'}}},
      {key: 'startDate_hour', label: 'Hour', data: {type: 'startDate', value: {unit: 'hour'}}}
    ]
  },
  {
    key: 'endDate',
    label: 'End Date of Process Instance',
    group: 'date',
    options: [
      {
        key: 'endDate_automatic',
        label: 'Automatic',
        data: {type: 'endDate', value: {unit: 'automatic'}}
      },
      {key: 'endDate_year', label: 'Year', data: {type: 'endDate', value: {unit: 'year'}}},
      {key: 'endDate_month', label: 'Month', data: {type: 'endDate', value: {unit: 'month'}}},
      {key: 'endDate_week', label: 'Week', data: {type: 'endDate', value: {unit: 'week'}}},
      {key: 'endDate_day', label: 'Day', data: {type: 'endDate', value: {unit: 'day'}}},
      {key: 'endDate_hour', label: 'Hour', data: {type: 'endDate', value: {unit: 'hour'}}}
    ]
  },
  {key: 'variable', label: 'Variable', group: 'variable', options: 'variable'},
  {key: 'userAssignee', label: 'Assignee', group: 'user', data: {type: 'assignee', value: null}},
  {
    key: 'userGroup',
    label: 'Candidate Group',
    group: 'user',
    data: {type: 'candidateGroup', value: null}
  }
];

export const visualization = [
  {key: 'number', label: 'Number', group: 'number', data: 'number'},
  {key: 'table', label: 'Table', group: 'table', data: 'table'},
  {key: 'bar', label: 'Bar Chart', group: 'chart', data: 'bar'},
  {key: 'line', label: 'Line Chart', group: 'chart', data: 'line'},
  {key: 'pie', label: 'Pie Chart', group: 'chart', data: 'pie'},
  {key: 'heat', label: 'Heatmap', group: 'heat', data: 'heat'}
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
  },
  userTask: {
    fn: ['table', 'chart', 'heat'],
    user: ['table', 'chart']
  }
};
