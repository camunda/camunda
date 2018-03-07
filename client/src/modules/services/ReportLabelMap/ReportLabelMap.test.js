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
