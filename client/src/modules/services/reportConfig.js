export const visualization = {
  number: {data: 'number', label: 'Number'},
  table: {data: 'table', label: 'Table'},
  bar: {data: 'bar', label: 'Bar Chart'},
  line: {data: 'line', label: 'Area Chart'},
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

const noneGroup = {entity: groupBy.none, then: [visualization.number]};
const startDateGroup = {
  entity: groupBy.startDate,
  then: [visualization.table, visualization.pie, visualization.line, visualization.bar]
};
const variableGroup = {
  entity: groupBy.variable,
  then: [visualization.table, visualization.pie, visualization.line, visualization.bar]
};
const flowNodeGroup = {
  entity: groupBy.flowNodes,
  then: [
    visualization.table,
    visualization.pie,
    visualization.line,
    visualization.bar,
    visualization.heat
  ]
};

export const view = {
  rawData: {
    data: {operation: 'rawData'},
    label: 'Raw Data',
    next: [{entity: groupBy.none, then: [visualization.table]}]
  },
  frequency: {
    data: {
      operation: 'count',
      property: 'frequency',
      entity: [
        {
          data: 'processInstance',
          label: 'Process Instances',
          next: [noneGroup, startDateGroup, variableGroup]
        },
        {
          data: 'flowNode',
          label: 'Flow Nodes',
          next: [flowNodeGroup]
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
          next: [noneGroup, startDateGroup, variableGroup]
        },
        {
          data: 'avg',
          label: 'Average',
          next: [noneGroup, startDateGroup, variableGroup]
        },
        {
          data: 'median',
          label: 'Median',
          next: [noneGroup, startDateGroup, variableGroup]
        },
        {
          data: 'max',
          label: 'Maximum',
          next: [noneGroup, startDateGroup, variableGroup]
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
          next: [flowNodeGroup]
        },
        {
          data: 'avg',
          label: 'Average',
          next: [flowNodeGroup]
        },
        {
          data: 'median',
          label: 'Median',
          next: [flowNodeGroup]
        },
        {
          data: 'max',
          label: 'Maximum',
          next: [flowNodeGroup]
        }
      ]
    },
    label: 'Flow Node Duration'
  }
};

/**
 * Retrieves the first level subobject form the configuration that corresponds to the entry. This does not fetch any submenu entries.
 *
 * @param config One of the configuration objects of the reportConfig service (view, groupBy, visualization)
 * @param entry One data entry of the configuration object. This corresponds to the payload sent to the backend
 */
const getObject = (config, entry) => {
  if (!entry || !config) {
    return;
  }
  for (const key of Object.keys(config)) {
    const {data} = config[key];
    const dataKeys = Object.keys(data);
    if (
      dataKeys.every(
        prop =>
          JSON.stringify(entry[prop]) === JSON.stringify(data[prop]) || Array.isArray(data[prop])
      )
    ) {
      return config[key];
    }
  }
};

/**
 * Construct a String representing the entry. Suitable for displaying to the user
 *
 * @param config One of the configuration objects of the reportConfig service (view, groupBy, visualization)
 * @param entry One data entry of the configuration object. This corresponds to the payload sent to the backend
 */
export const getLabelFor = (config, entry) => {
  const obj = getObject(config, entry);

  if (obj) {
    const {data, label} = obj;

    if (data.type === 'variable') {
      return `${label}: ${entry.value.name}`;
    }

    const dataKeys = Object.keys(data);

    const submenu = dataKeys.find(key => Array.isArray(data[key]));
    if (submenu) {
      return `${label}: ${getLabelFor(data[submenu], entry[submenu])}`;
    }
    return label;
  }
};

const getNextObject = (view, targetView) => {
  const {data} = view;
  const dataKeys = Object.keys(data);

  let next = view.next;

  const submenu = dataKeys.find(key => Array.isArray(data[key]));
  if (submenu) {
    next = getObject(data[submenu], targetView[submenu]).next;
  }

  return next;
};

/**
 * Checks whether a certain combination of view, groupby and visualization is allowed.
 */
export const isAllowed = (targetView, targetGroupBy, targetVisualization) => {
  const viewObj = getObject(view, targetView);
  const groupByObj = getObject(groupBy, targetGroupBy);
  const visualizationObj = getObject(visualization, targetVisualization);

  if (viewObj && groupByObj) {
    const next = getNextObject(viewObj, targetView);
    const allowed = next.find(({entity}) => entity === groupByObj);
    if (!allowed) {
      return false;
    }

    if (visualizationObj) {
      return !!allowed.then.find(potentialVis => visualizationObj === potentialVis);
    }
  }

  return true;
};

/**
 * Based on a given view (and optional groupby), returns the next payload data, if it is unambiguous.
 */
export const getNext = (targetView, targetGroupBy) => {
  const viewObj = getObject(view, targetView);
  const groupByObj = getObject(groupBy, targetGroupBy);

  const next = getNextObject(viewObj, targetView);

  if (next.length === 1 && !targetGroupBy) {
    return next[0].entity.data;
  }

  if (groupByObj) {
    const allowed = next.find(({entity}) => entity === groupByObj);
    if (!allowed) {
      return;
    }

    if (allowed.then.length === 1) {
      return allowed.then[0].data;
    }
  }
};
