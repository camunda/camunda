const options = {
  view: [
    {key: 'rawData_ignored_ignored', label: 'Raw Data'},
    {key: 'count_processInstance_frequency', label: 'Count Process Instance Frequency'},
    {key: 'count_flowNode_frequency', label: 'Count Flow Node Frequency'},
    {key: 'avg_processInstance_duration', label: 'Average Process Instance Duration'},
    {key: 'avg_flowNode_duration', label: 'Average Flow Node Duration'}
  ],
  groupBy: [
    {key: 'none_null', label: 'None'},
    {key: 'flowNode_null', label: 'Flow Nodes'},
    {key: 'startDate_year', label: 'Start Date of Process Instance - Year'},
    {key: 'startDate_month', label: 'Start Date of Process Instance - Month'},
    {key: 'startDate_week', label: 'Start Date of Process Instance - Week'},
    {key: 'startDate_day', label: 'Start Date of Process Instance - Day'},
    {key: 'startDate_hour', label: 'Start Date of Process Instance - Hour'}
  ],
  visualization: [
    {key: 'number', label: 'Number'},
    {key: 'table', label: 'Table'},
    {key: 'bar', label: 'Bar Chart'},
    {key: 'line', label: 'Area Chart'},
    {key: 'pie', label: 'Pie Chart'},
    {key: 'heat', label: 'Heatmap'},
    {key: 'json', label: 'JSON Dump'}
  ]
};

const allowedOptionsMatrix = {
  rawData_ignored_ignored: {
    none_null: ['table']
  },
  count_processInstance_frequency: {
    none_null: ['number'],
    startDate_year: ['table', 'pie', 'line', 'bar'],
    startDate_month: ['table', 'pie', 'line', 'bar'],
    startDate_week: ['table', 'pie', 'line', 'bar'],
    startDate_day: ['table', 'pie', 'line', 'bar'],
    startDate_hour: ['table', 'pie', 'line', 'bar']
  },
  count_flowNode_frequency: {
    flowNode_null: ['heat', 'pie', 'line', 'bar', 'table']
  },
  avg_processInstance_duration: {
    none_null: ['number'],
    startDate_year: ['table', 'pie', 'line', 'bar'],
    startDate_month: ['table', 'pie', 'line', 'bar'],
    startDate_week: ['table', 'pie', 'line', 'bar'],
    startDate_day: ['table', 'pie', 'line', 'bar'],
    startDate_hour: ['table', 'pie', 'line', 'bar']
  },
  avg_flowNode_duration: {
    flowNode_null: ['heat', 'pie', 'line', 'bar', 'table']
  }
};

const reportLabelMap = {
  getTheRightCombination: function(view, groupBy, visualization) {
    if (!allowedOptionsMatrix[view]) {
      return {
        view: this.keyToObject('', 'view'),
        groupBy: this.keyToObject('', 'groupBy'),
        visualization: this.keyToObject('', 'visualization')
      };
    }

    if (!allowedOptionsMatrix[view][groupBy]) {
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

    if (!allowedOptionsMatrix[view][groupBy].includes(visualization)) {
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
        return Object.keys(allowedOptionsMatrix[view])[0];
      } else return '';
    }

    if (type === 'visualization') {
      const newGroupBy = this.getTheOnlyOption('groupBy', view, groupBy) || groupBy;
      if (
        allowedOptionsMatrix[view][newGroupBy] &&
        allowedOptionsMatrix[view][newGroupBy].length === 1
      ) {
        return allowedOptionsMatrix[view][newGroupBy][0];
      } else return '';
    }
  },

  getEnabledOptions: function(type, view, groupBy) {
    if (type !== 'view' && !allowedOptionsMatrix[view]) return null;

    if (type === 'groupBy') {
      return Object.keys(allowedOptionsMatrix[view]);
    }
    if (type === 'visualization') {
      return allowedOptionsMatrix[view][groupBy];
    }
  },

  view: 'view',
  groupBy: 'groupBy',

  objectToLabel: function(object, type) {
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
      const unit = object.unit;
      return `${groupByType}_${unit}`;
    }
  },

  getOptions: function(type) {
    return options[type];
  },

  keyToObject: function(key, type) {
    const data = key.split('_');
    if (type === this.view) {
      if (key === '') return {operation: '', entity: '', property: ''};

      return {operation: data[0], entity: data[1], property: data[2]};
    } else if (type === this.groupBy) {
      if (key === '') return {type: '', unit: null};

      const type = data[0];
      const unit = data[1] === 'null' ? null : data[1];
      return {type, unit};
    } else {
      return key;
    }
  }
};

export default reportLabelMap;
