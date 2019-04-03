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
  pie: {data: 'pie', label: 'Pie Chart'}
};

export const groupBy = {
  none: {data: {type: 'none'}, label: 'None'},
  rules: {data: {type: 'matchedRule'}, label: 'Rules'},
  evaluationDate: {
    data: {
      type: 'evaluationDateTime',
      value: [
        {data: {unit: 'automatic'}, label: 'Automatic'},
        {data: {unit: 'year'}, label: 'Year'},
        {data: {unit: 'month'}, label: 'Month'},
        {data: {unit: 'week'}, label: 'Week'},
        {data: {unit: 'day'}, label: 'Day'},
        {data: {unit: 'hour'}, label: 'Hour'}
      ]
    },
    label: 'Evaluation Date'
  },
  inputVariable: {
    data: {
      type: 'inputVariable',
      value: []
    },
    label: 'Input Variable'
  },
  outputVariable: {
    data: {
      type: 'outputVariable',
      value: []
    },
    label: 'Output Variable'
  }
};

const combinations = {
  rawData: [{entity: groupBy.none, then: [visualization.table]}],
  frequency: [
    {
      entity: groupBy.none,
      then: [visualization.number]
    },
    {
      entity: groupBy.rules,
      then: [visualization.table]
    },
    {
      entity: groupBy.evaluationDate,
      then: [visualization.table, visualization.pie, visualization.line, visualization.bar]
    },
    {
      entity: groupBy.inputVariable,
      then: [visualization.table, visualization.pie, visualization.line, visualization.bar]
    },
    {
      entity: groupBy.outputVariable,
      then: [visualization.table, visualization.pie, visualization.line, visualization.bar]
    }
  ]
};

export const view = {
  rawData: {
    data: {operation: 'rawData'},
    label: 'Raw Data',
    next: combinations.rawData
  },
  frequency: {
    data: {operation: 'count', property: 'frequency'},
    label: 'Evaluation Count',
    next: combinations.frequency
  }
};
