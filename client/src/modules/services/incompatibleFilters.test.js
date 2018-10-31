import {incompatibleFilters} from './incompatibleFilters';

it('should return true if filters contains completedInstancesOnly and runningInstancesOnly together', () => {
  expect(
    incompatibleFilters([
      {type: 'completedInstancesOnly', data: null},
      {type: 'runningInstancesOnly', data: null}
    ])
  ).toBe(true);
});

it('should return true if filters contains endDate and runningInstancesOnly together', () => {
  expect(
    incompatibleFilters([{type: 'endDate', data: null}, {type: 'runningInstancesOnly', data: null}])
  ).toBe(true);
});

it('should return false if filters contains only completedInstancesOnly', () => {
  expect(incompatibleFilters([{type: 'completedInstancesOnly', data: null}])).toBe(false);
});
