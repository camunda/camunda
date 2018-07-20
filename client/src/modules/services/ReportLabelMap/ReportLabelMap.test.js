import {default as reportLabelMap} from './ReportLabelMap';

it('should return all possible options for a type', () => {
  expect(reportLabelMap.getOptions(reportLabelMap.view)).toHaveLength(8);
});

it('should create a report view object for a given key', () => {
  expect(reportLabelMap.keyToObject('count_flowNode_frequency', reportLabelMap.view)).toEqual({
    operation: 'count',
    entity: 'flowNode',
    property: 'frequency'
  });
});

it('should create a report group by object for a given key', () => {
  expect(reportLabelMap.keyToObject('startDate_{"unit":"year"}', reportLabelMap.groupBy)).toEqual({
    type: 'startDate',
    value: {unit: 'year'}
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
    reportLabelMap.objectToKey({type: 'startDate', value: {unit: 'year'}}, reportLabelMap.groupBy)
  ).toEqual('startDate_{"unit":"year"}');
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
    reportLabelMap.objectToLabel({type: 'startDate', value: {unit: 'year'}}, reportLabelMap.groupBy)
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
    'flowNodes_null'
  );
});

it('should return the right new combination given the old one', () => {
  expect(reportLabelMap.getTheRightCombination('rawData_ignored_ignored', '', '')).toEqual({
    groupBy: {type: 'none', value: null},
    view: {entity: 'ignored', operation: 'rawData', property: 'ignored'},
    visualization: 'table'
  });

  expect(
    reportLabelMap.getTheRightCombination('avg_flowNode_duration', 'none_null', 'table')
  ).toEqual({
    groupBy: {type: 'flowNodes', value: null},
    view: {entity: 'flowNode', operation: 'avg', property: 'duration'},
    visualization: ''
  });

  expect(
    reportLabelMap.getTheRightCombination('avg_processInstance_duration', 'none_null', 'table')
  ).toEqual({
    groupBy: {type: 'none', value: null},
    view: {entity: 'processInstance', operation: 'avg', property: 'duration'},
    visualization: 'number'
  });

  expect(
    reportLabelMap.getTheRightCombination(
      'avg_processInstance_duration',
      'startDate_{"unit":"year"}',
      'number'
    )
  ).toEqual({
    groupBy: {type: 'startDate', value: {unit: 'year'}},
    view: {entity: 'processInstance', operation: 'avg', property: 'duration'},
    visualization: ''
  });

  //if valid combination passed, same combination returned
  expect(
    reportLabelMap.getTheRightCombination('avg_flowNode_duration', 'flowNodes_null', 'table')
  ).toEqual({
    groupBy: {type: 'flowNodes', value: null},
    view: {entity: 'flowNode', operation: 'avg', property: 'duration'},
    visualization: 'table'
  });
});

it('should return all possible options for given view and groupBy options', () => {
  expect(reportLabelMap.getEnabledOptions('groupBy', 'avg_flowNode_duration', '')).toEqual([
    'flowNodes'
  ]);

  expect(
    reportLabelMap.getEnabledOptions('visualization', 'avg_flowNode_duration', 'flowNodes_null')
  ).toEqual(['heat', 'pie', 'line', 'bar', 'table']);

  expect(reportLabelMap.getEnabledOptions('groupBy', 'avg_processInstance_duration', '')).toEqual([
    'none',
    'startDate',
    'variable'
  ]);
});
