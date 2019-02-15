import {uniteResults} from './service';

it('should unify the keys of all result object by filling empty ones with null', () => {
  expect(uniteResults([{a: 1, b: 2}, {b: 1}], ['a', 'b'])).toEqual([{a: 1, b: 2}, {a: null, b: 1}]);
});
