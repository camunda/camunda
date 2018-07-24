const options = {
  view: [
    {key: 'rawData_null_null', label: 'Raw Data'},
    {key: 'count_processInstance_frequency', label: 'Count Process Instance Frequency'},
    {key: 'count_flowNode_frequency', label: 'Count Flow Node Frequency'},
    {key: 'avg_processInstance_duration', label: 'Average Process Instance Duration'},
    {key: 'avg_flowNode_duration', label: 'Average Flow Node Duration'},
    {key: 'min_flowNode_duration', label: 'Minimum Flow Node Duration'},
    {key: 'median_flowNode_duration', label: 'Median Flow Node Duration'},
    {key: 'max_flowNode_duration', label: 'Max Flow Node Duration'}
  ],
  groupBy: [
    {key: 'none_null', label: 'None'},
    {key: 'flowNodes_null', label: 'Flow Nodes'},
    {key: 'startDate_{"unit":"year"}', label: 'Start Date of Process Instance - Year'},
    {key: 'startDate_{"unit":"month"}', label: 'Start Date of Process Instance - Month'},
    {key: 'startDate_{"unit":"week"}', label: 'Start Date of Process Instance - Week'},
    {key: 'startDate_{"unit":"day"}', label: 'Start Date of Process Instance - Day'},
    {key: 'startDate_{"unit":"hour"}', label: 'Start Date of Process Instance - Hour'}
  ],
  visualization: [
    {key: 'number', label: 'Number'},
    {key: 'table', label: 'Table'},
    {key: 'bar', label: 'Bar Chart'},
    {key: 'line', label: 'Area Chart'},
    {key: 'pie', label: 'Pie Chart'},
    {key: 'heat', label: 'Heatmap'}
  ]
};

const allowedOptionsMatrix = {
  rawData_null_null: {
    none: ['table']
  },
  count_processInstance_frequency: {
    none: ['number'],
    startDate: ['table', 'pie', 'line', 'bar'],
    variable: ['table', 'pie', 'line', 'bar']
  },
  count_flowNode_frequency: {
    flowNodes: ['heat', 'pie', 'line', 'bar', 'table']
  },
  avg_processInstance_duration: {
    none: ['number'],
    startDate: ['table', 'pie', 'line', 'bar'],
    variable: ['table', 'pie', 'line', 'bar']
  },
  avg_flowNode_duration: {
    flowNodes: ['heat', 'pie', 'line', 'bar', 'table']
  },
  min_flowNode_duration: {
    flowNodes: ['heat', 'pie', 'line', 'bar', 'table']
  },
  median_flowNode_duration: {
    flowNodes: ['heat', 'pie', 'line', 'bar', 'table']
  },
  max_flowNode_duration: {
    flowNodes: ['heat', 'pie', 'line', 'bar', 'table']
  }
};

const reportLabelMap = {
  getTheRightCombination: function(view, groupBy, visualization) {
    const groupByType = getGroupByType(groupBy);
    if (!allowedOptionsMatrix[view]) {
      return {
        view: this.keyToObject('', 'view'),
        groupBy: this.keyToObject('', 'groupBy'),
        visualization: this.keyToObject('', 'visualization')
      };
    }

    if (!allowedOptionsMatrix[view][groupByType]) {
      const theOnlyGroupBy = this.keyToObject(
        this.getTheOnlyOption('groupBy', view, groupBy),
        'groupBy'
      );
      const theOnlyVisualization = this.keyToObject(
        this.getTheOnlyOption('visualization', view, groupBy),
        'visualization'
      );

      return {
        view: this.keyToObject(view, 'view'),
        groupBy: theOnlyGroupBy || this.keyToObject('', 'groupBy'),
        visualization: theOnlyVisualization || this.keyToObject('', 'visualization')
      };
    }

    if (!allowedOptionsMatrix[view][groupByType].includes(visualization)) {
      return {
        view: this.keyToObject(view, 'view'),
        groupBy: this.keyToObject(groupBy, 'groupBy'),
        visualization:
          this.getTheOnlyOption('visualization', view, groupBy) ||
          this.keyToObject('', 'visualization')
      };
    }

    return {
      view: this.keyToObject(view, 'view'),
      groupBy: this.keyToObject(groupBy, 'groupBy'),
      visualization: this.keyToObject(visualization, 'visualization')
    };
  },

  getTheOnlyOption: function(type, view, groupBy) {
    if (!allowedOptionsMatrix[view]) return '';

    if (type === 'groupBy') {
      if (Object.keys(allowedOptionsMatrix[view]).length === 1) {
        return Object.keys(allowedOptionsMatrix[view])[0] + '_null';
      } else return '';
    }

    if (type === 'visualization') {
      const newGroupBy = this.getTheOnlyOption('groupBy', view, groupBy) || groupBy;
      const groupByType = getGroupByType(newGroupBy);
      if (
        allowedOptionsMatrix[view][groupByType] &&
        allowedOptionsMatrix[view][groupByType].length === 1
      ) {
        return allowedOptionsMatrix[view][groupByType][0];
      } else return '';
    }
  },

  getEnabledOptions: function(type, view, groupBy) {
    if (type !== 'view' && !allowedOptionsMatrix[view]) return null;

    if (type === 'groupBy') {
      return Object.keys(allowedOptionsMatrix[view]);
    }
    if (type === 'visualization') {
      return [...allowedOptionsMatrix[view][getGroupByType(groupBy)]];
    }
  },

  view: 'view',
  groupBy: 'groupBy',

  objectToLabel: function(object, type) {
    if (type === 'groupBy' && object.type === 'variable') {
      return object.value.name;
    }

    const key = this.objectToKey(object, type);
    const foundObj = options[type].find(elem => elem.key === key);
    return foundObj ? foundObj.label : foundObj;
  },

  objectToKey: function(object, type) {
    if (typeof object === 'string') {
      return object;
    }
    if (type === this.view) {
      const {operation, entity, property} = object;
      if (operation === '') return '';
      return `${operation}_${entity}_${property}`;
    } else if (type === this.groupBy) {
      const groupByType = object.type;
      if (groupByType === '') return '';
      const value = object.value;
      return `${groupByType}_${JSON.stringify(value)}`;
    }
  },

  getOptions: function(type) {
    return [...options[type]];
  },

  keyToObject: function(key, type) {
    const data = key.split('_');
    if (type === this.view) {
      if (key === '') return {operation: '', entity: '', property: ''};

      return {operation: data[0], entity: data[1], property: data[2]};
    } else if (type === this.groupBy) {
      if (key === '') return {type: '', value: null};

      const type = data[0];
      const value = data[1] === 'null' ? null : JSON.parse(data[1]);
      return {type, value};
    } else {
      return key;
    }
  }
};

function getGroupByType(groupBy) {
  return groupBy.split('_')[0];
}

export default reportLabelMap;
