import {view, groupBy, visualization, getLabelFor, isAllowed, getNext} from './reportConfig';

it('should get a label for a simple visualization', () => {
  expect(getLabelFor(visualization, 'heat')).toBe('Heatmap');
});

it('should get a label for a complex view', () => {
  expect(
    getLabelFor(view, {operation: 'count', entity: 'processInstance', property: 'frequency'})
  ).toBe('Count Frequency of: Process Instances');
});

it('should get a label for gorup by variables', () => {
  expect(getLabelFor(groupBy, {type: 'variable', value: {name: 'aName', type: 'String'}})).toBe(
    'Variable: aName'
  );
});

it('should return the groupBy based on the view if the groupBy is unambiguous', () => {
  expect(getNext({operation: 'count', entity: 'flowNode', property: 'frequency'})).toEqual({
    type: 'flowNodes'
  });
});

it('should return the visualization based on the view and groupBy if the visualization is unambiguous', () => {
  expect(
    getNext({operation: 'avg', entity: 'processInstance', property: 'duration'}, {type: 'none'})
  ).toEqual('number');
});

it('should return undefined if an unambiguous next config param could not be found', () => {
  expect(getNext({operation: 'avg', entity: 'processInstance', property: 'duration'})).toBe(
    undefined
  );
});

it('should always allow view selection', () => {
  expect(isAllowed({operation: 'rawData'})).toBe(true);
});

it('should allow only groupBy options that make sense for the selected view', () => {
  expect(isAllowed({operation: 'rawData'}, {type: 'none'})).toBe(true);
  expect(isAllowed({operation: 'rawData'}, {type: 'flowNodes'})).toBe(false);
});

it('should allow only visualization options that make sense for the selected view and group', () => {
  expect(isAllowed({operation: 'rawData'}, {type: 'none'}, 'table')).toBe(true);
  expect(isAllowed({operation: 'rawData'}, {type: 'none'}, 'heat')).toBe(false);

  expect(
    isAllowed(
      {
        operation: 'avg',
        entity: 'processInstance',
        property: 'duration'
      },
      {
        type: 'startDate',
        value: {
          unit: 'day'
        }
      },
      'pie'
    )
  ).toBe(true);
  expect(
    isAllowed(
      {
        operation: 'avg',
        entity: 'processInstance',
        property: 'duration'
      },
      {
        type: 'none'
      },
      'pie'
    )
  ).toBe(false);
});
