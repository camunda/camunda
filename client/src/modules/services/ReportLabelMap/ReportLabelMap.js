const options = {
    view: [
      {
        key: 'rawData_ignored_ignored',
        label: 'Raw Data',
        allowedNext:['none_null']
      },
      {
        key: 'count_processInstance_frequency',
        label: 'Count Process Instance Frequency',
        allowedNext:['none_null','startDate_year', 'startDate_month', 'startDate_week', 'startDate_day', 'startDate_hour']
      },
      {
        key: 'count_flowNode_frequency',
        label: 'Count Flow Node Frequency',
        allowedNext:['flowNode_null']
      },
      {
        key: 'avg_processInstance_duration',
        label: 'Average Process Instance Duration',
        allowedNext:['none_null','startDate_year', 'startDate_month', 'startDate_week', 'startDate_day', 'startDate_hour']
      },
      {
      key: 'avg_flowNode_duration',
      label: 'Average Flow Node Duration',
      allowedNext:['flowNode_null']
      }
    ],
    groupBy: [
      {key: 'none_null', label: 'None', allowedNext:['table', 'number']},
      {key: 'flowNode_null', label: 'Flow Nodes', allowedNext:['heat', 'pie', 'line', 'bar', 'table']},
      {key: 'startDate_year', label: 'Start Date of Process Instance - Year', allowedNext:['table', 'pie', 'line', 'bar']},
      {key: 'startDate_month', label: 'Start Date of Process Instance - Month', allowedNext:['table', 'pie', 'line', 'bar']},
      {key: 'startDate_week', label: 'Start Date of Process Instance - Week', allowedNext:['table', 'pie', 'line', 'bar']},
      {key: 'startDate_day', label: 'Start Date of Process Instance - Day', allowedNext:['table', 'pie', 'line', 'bar']},
      {key: 'startDate_hour', label: 'Start Date of Process Instance - Hour', allowedNext:['table', 'pie', 'line', 'bar']}
    ],
    visualizeAs: [
      {key: 'number', label: 'Number'},
      {key: 'table', label: 'Table'},
      {key: 'bar', label: 'Bar Chart'},
      {key: 'line', label: 'Area Chart'},
      {key: 'pie', label: 'Pie Chart'},
      {key: 'heat', label: 'Heatmap'},
      {key: 'json', label: 'JSON Dump'}
    ]
  };

  const reportLabelMap = {

    view: 'view',
    groupBy: 'groupBy',

    objectToLabel: function(object, type) {
      const key = this.objectToKey(object, type);
      const foundObj = options[type].find(elem => elem.key === key);
      return foundObj? foundObj.label: foundObj;
    },

    objectToKey: function(object, type) {
      if(typeof(object) === 'string') {
        return object;
      }
      if(type === this.view) {
        const {operation, entity, property} = object;
        return `${operation}_${entity}_${property}`;
      } else if(type === this.groupBy) {
        const groupByType = object.type;
        const unit = object.unit;
        return `${groupByType}_${unit}`;
      }
    },

    getOptions: function(type) {
      return options[type];
    },

    keyToObject: function(key, type) {
      const data = key.split('_');
      if(type === this.view) {
        if (key === '') return {operation: '', entity: '', property: ''};

        return {operation: data[0], entity: data[1], property: data[2]};
      } else if(type === this.groupBy) {
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
