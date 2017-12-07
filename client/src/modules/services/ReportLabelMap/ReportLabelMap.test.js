import {mapper} from './ReportLabelMap';

it('should return all possible options for a type', () => {
  expect(mapper.getOptions(mapper.view)).toHaveLength(5);
});

it('should create a report view object for a given key', () => {
  expect(mapper.keyToObject('count_flowNode_frequency', mapper.view)).toEqual({operation: 'count', entity: 'flowNode', property: 'frequency'});
});

it('should create a report group by object for a given key', () => {
  expect(mapper.keyToObject('startDate_year', mapper.groupBy)).toEqual({type: 'startDate', unit: 'year'});
});

it('should compose a key for a given view object', () => {
  expect(mapper.objectToKey({operation: 'count', entity: 'flowNode', property: 'frequency'}, mapper.view)).toEqual('count_flowNode_frequency');
});

it('should compose a key for a given group object', () => {
  expect(mapper.objectToKey({type: 'startDate', unit: 'year'}, mapper.groupBy)).toEqual('startDate_year');
});

it('should extract a label for a given view object', () => {
  expect(mapper.objectToLabel({operation: 'count', entity: 'flowNode', property: 'frequency'}, mapper.view)).toEqual('Count Flow Node Frequency');
});

it('should compose a key for a given group object', () => {
  expect(mapper.objectToLabel({type: 'startDate', unit: 'year'}, mapper.groupBy)).toEqual('Start Date of Process Instance - Year');
});

  
