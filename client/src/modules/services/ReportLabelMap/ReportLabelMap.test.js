import {default as reportLabelMap} from './ReportLabelMap';

it('should return all possible options for a type', () => {
  expect(reportLabelMap.getOptions(reportLabelMap.view)).toHaveLength(5);
});

it('should create a report view object for a given key', () => {
  expect(reportLabelMap.keyToObject('count_flowNode_frequency', reportLabelMap.view)).toEqual({
    operation: 'count',
    entity: 'flowNode',
    property: 'frequency'
  });
});

it('should create a report group by object for a given key', () => {
  expect(reportLabelMap.keyToObject('startDate_year', reportLabelMap.groupBy)).toEqual({
    type: 'startDate',
    unit: 'year'
  });
});

it('should compose a key for a given view object', () => {
  expect(
    reportLabelMap.objectToKey(
      {operation: 'count', entity: 'flowNode', property: 'frequency'},
      reportLabelMap.view
    )
  ).toEqual('count_flowNode_frequency');
});

it('should compose a key for a given group object', () => {
  expect(
    reportLabelMap.objectToKey({type: 'startDate', unit: 'year'}, reportLabelMap.groupBy)
  ).toEqual('startDate_year');
});

it('should extract a label for a given view object', () => {
  expect(
    reportLabelMap.objectToLabel(
      {operation: 'count', entity: 'flowNode', property: 'frequency'},
      reportLabelMap.view
    )
  ).toEqual('Count Flow Node Frequency');
});

it('should compose a key for a given group object', () => {
  expect(
    reportLabelMap.objectToLabel({type: 'startDate', unit: 'year'}, reportLabelMap.groupBy)
  ).toEqual('Start Date of Process Instance - Year');
});

it('should return the right option if only one is allowed', () => {
  expect(reportLabelMap.getTheOnlyOption('groupBy', 'rawData_ignored_ignored', '')).toEqual(
    'none_null'
  );

  expect(
    reportLabelMap.getTheOnlyOption('visualization', 'rawData_ignored_ignored', 'none_null')
  ).toEqual('table');

  expect(reportLabelMap.getTheOnlyOption('groupBy', 'avg_flowNode_duration', '')).toEqual(
    'flowNode_null'
  );
});

it('should return the right new combination given the old one', () => {
  expect(reportLabelMap.getTheRightCombination('rawData_ignored_ignored', '', '')).toEqual({
    groupBy: {type: 'none', unit: null},
    view: {entity: 'ignored', operation: 'rawData', property: 'ignored'},
    visualization: 'table'
  });

  expect(
    reportLabelMap.getTheRightCombination('avg_flowNode_duration', 'none_null', 'table')
  ).toEqual({
    groupBy: {type: 'flowNode', unit: null},
    view: {entity: 'flowNode', operation: 'avg', property: 'duration'},
    visualization: ''
  });

  expect(
    reportLabelMap.getTheRightCombination('avg_processInstance_duration', 'none_null', 'table')
  ).toEqual({
    groupBy: {type: 'none', unit: null},
    view: {entity: 'processInstance', operation: 'avg', property: 'duration'},
    visualization: 'number'
  });

  expect(
    reportLabelMap.getTheRightCombination(
      'avg_processInstance_duration',
      'startDate_year',
      'number'
    )
  ).toEqual({
    groupBy: {type: 'startDate', unit: 'year'},
    view: {entity: 'processInstance', operation: 'avg', property: 'duration'},
    visualization: ''
  });

  //if valid combination passed, same combination returned
  expect(
    reportLabelMap.getTheRightCombination('avg_flowNode_duration', 'flowNode_null', 'table')
  ).toEqual({
    groupBy: {type: 'flowNode', unit: null},
    view: {entity: 'flowNode', operation: 'avg', property: 'duration'},
    visualization: 'table'
  });
});

it('should return all possible options for given view and groupBy options', () => {
  expect(reportLabelMap.getEnabledOptions('groupBy', 'avg_flowNode_duration', '')).toEqual([
    'flowNode_null'
  ]);

  expect(
    reportLabelMap.getEnabledOptions('visualization', 'avg_flowNode_duration', 'flowNode_null')
  ).toEqual(['heat', 'pie', 'line', 'bar', 'table']);

  expect(reportLabelMap.getEnabledOptions('groupBy', 'avg_processInstance_duration', '')).toEqual([
    'none_null',
    'startDate_year',
    'startDate_month',
    'startDate_week',
    'startDate_day',
    'startDate_hour'
  ]);
});
