/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const visualization = {
  number: {data: 'number', label: 'Number'},
  table: {data: 'table', label: 'Table'},
  bar: {data: 'bar', label: 'Bar Chart'},
  line: {data: 'line', label: 'Line Chart'},
  pie: {data: 'pie', label: 'Pie Chart'},
  heat: {data: 'heat', label: 'Heatmap'}
};

export const groupBy = {
  none: {data: {type: 'none'}, label: 'None'},
  flowNodes: {data: {type: 'flowNodes'}, label: 'Flow Nodes'},
  startDate: {
    data: {
      type: 'startDate',
      value: [
        {data: {unit: 'automatic'}, label: 'Automatic'},
        {data: {unit: 'year'}, label: 'Year'},
        {data: {unit: 'month'}, label: 'Month'},
        {data: {unit: 'week'}, label: 'Week'},
        {data: {unit: 'day'}, label: 'Day'},
        {data: {unit: 'hour'}, label: 'Hour'}
      ]
    },
    label: 'Start Date of Process Instance'
  },
  variable: {
    data: {
      type: 'variable',
      value: []
    },
    label: 'Variable'
  }
};

const combinations = {
  rawData: [{entity: groupBy.none, then: [visualization.table]}],
  flowNodes: [
    {
      entity: groupBy.flowNodes,
      then: [
        visualization.table,
        visualization.pie,
        visualization.line,
        visualization.bar,
        visualization.heat
      ]
    }
  ],
  instance: [
    {entity: groupBy.none, then: [visualization.number]},
    {
      entity: groupBy.startDate,
      then: [visualization.table, visualization.pie, visualization.line, visualization.bar]
    },
    {
      entity: groupBy.variable,
      then: [visualization.table, visualization.pie, visualization.line, visualization.bar]
    }
  ]
};

export const view = {
  rawData: {
    data: {property: 'rawData', entity: null},
    label: 'Raw Data',
    next: combinations.rawData
  },
  processInstance: {
    label: 'Process Instance',
    data: {
      entity: 'processInstance',
      property: [
        {
          data: 'frequency',
          label: 'Count',
          next: combinations.instance
        },
        {
          data: 'duration',
          label: 'Duration',
          next: combinations.instance
        }
      ]
    }
  },
  flowNode: {
    label: 'Flow Node',
    data: {
      entity: 'flowNode',
      property: [
        {
          data: 'frequency',
          label: 'Count',
          next: combinations.flowNodes
        },
        {
          data: 'duration',
          label: 'Duration',
          next: combinations.flowNodes
        }
      ]
    }
  },
  userTaskDuration: {
    label: 'User Task Duration',
    data: {
      entity: 'userTask',
      property: [
        {data: 'idleDuration', label: 'Idle', next: combinations.flowNodes},
        {data: 'workDuration', label: 'Work', next: combinations.flowNodes},
        {data: 'duration', label: 'Total', next: combinations.flowNodes}
      ]
    }
  }
};
