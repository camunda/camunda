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
    data: {operation: 'rawData'},
    label: 'Raw Data',
    next: combinations.rawData
  },
  frequency: {
    data: {
      operation: 'count',
      property: 'frequency',
      entity: [
        {
          data: 'processInstance',
          label: 'Process Instances',
          next: combinations.instance
        },
        {
          data: 'flowNode',
          label: 'Flow Nodes',
          next: combinations.flowNodes
        }
      ]
    },
    label: 'Count Frequency of'
  },
  processInstanceDuration: {
    data: {
      property: 'duration',
      entity: 'processInstance',
      operation: [
        {
          data: 'min',
          label: 'Minimum',
          next: combinations.instance
        },
        {
          data: 'avg',
          label: 'Average',
          next: combinations.instance
        },
        {
          data: 'median',
          label: 'Median',
          next: combinations.instance
        },
        {
          data: 'max',
          label: 'Maximum',
          next: combinations.instance
        }
      ]
    },
    label: 'Process Instance Duration'
  },
  flowNodeDuration: {
    data: {
      property: 'duration',
      entity: 'flowNode',
      operation: [
        {
          data: 'min',
          label: 'Minimum',
          next: combinations.flowNodes
        },
        {
          data: 'avg',
          label: 'Average',
          next: combinations.flowNodes
        },
        {
          data: 'median',
          label: 'Median',
          next: combinations.flowNodes
        },
        {
          data: 'max',
          label: 'Maximum',
          next: combinations.flowNodes
        }
      ]
    },
    label: 'Flow Node Duration'
  },
  userTaskDuration: {
    label: 'User Task Duration',
    data: {
      entity: 'userTask',
      operation: 'avg',
      property: [
        {data: 'idleDuration', label: 'Idle', next: combinations.flowNodes},
        {data: 'workDuration', label: 'Work', next: combinations.flowNodes},
        {data: 'duration', label: 'Total', next: combinations.flowNodes}
      ]
    }
  }
};
