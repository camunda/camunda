import {uniteResults, getFormattedLabels, getBodyRows, getLineTargetValues} from './service';

jest.mock('request', () => ({
  get: jest.fn()
}));

jest.mock('services', () => ({
  formatters: {
    convertToMilliseconds: v => v
  }
}));

it('should unify the keys of all result object by filling empty ones with null', () => {
  expect(uniteResults([{a: 1, b: 2}, {b: 1}], ['a', 'b'])).toEqual([{a: 1, b: 2}, {a: null, b: 1}]);
});

it('should return correctly formatted body rows', () => {
  expect(getBodyRows([{a: 1, b: 2}, {a: '', b: 1}], ['a', 'b'], v => v, false, [100, 100])).toEqual(
    [['a', 1, ''], ['b', 2, 1]]
  );
});

it('should return correct table label structure', () => {
  expect(
    getFormattedLabels([['key', 'value'], ['key', 'value']], ['Report A', 'Report B'], false)
  ).toEqual([{label: 'Report A', columns: ['value']}, {label: 'Report B', columns: ['value']}]);
});

it('should get line chart target values from normal values', () => {
  const data = {foo: 123, bar: 5, dar: 5};
  const values = {target: 10};
  const targetValues = getLineTargetValues(Object.values(data), values);
  expect(targetValues).toEqual([null, 5, 5]);
});
