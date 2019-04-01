import {filterEntitiesBySearch} from './service';

it('should filter entities by search correctly', () => {
  expect(filterEntitiesBySearch([{name: 'test name'}, {name: 'Another Name'}], 'Test')).toEqual([
    {name: 'test name'}
  ]);
});
