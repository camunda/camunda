import {uniteResults, getFormattedLabels, getBodyRows} from './service';

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
  expect(
    getBodyRows([{a: 1, b: 2}, {a: '', b: 0}], ['a', 'b'], v => v, false, [100, 100], true)
  ).toEqual([['a', 1, ''], ['b', 2, 0]]);
});

it('should hide absolute values when sepcified from body rows', () => {
  expect(
    getBodyRows([{a: 1, b: 2}, {a: '', b: 1}], ['a', 'b'], v => v, false, [100, 100], false)
  ).toEqual([['a'], ['b']]);
});

it('should return correct table label structure', () => {
  expect(
    getFormattedLabels([['key', 'value'], ['key', 'value']], ['Report A', 'Report B'], false, true)
  ).toEqual([{label: 'Report A', columns: ['value']}, {label: 'Report B', columns: ['value']}]);
});

it('should hide absolute values when specified from labels', () => {
  expect(
    getFormattedLabels([['key', 'value'], ['key', 'value']], ['Report A', 'Report B'], false, false)
  ).toEqual([{columns: [], label: 'Report A'}, {columns: [], label: 'Report B'}]);
});
