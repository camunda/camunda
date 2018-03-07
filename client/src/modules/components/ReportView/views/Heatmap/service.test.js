import {calculateTargetValueHeat} from './service';

jest.mock('heatmap.js', () => {});

describe('calculateTargetValueHeat', () => {
  it('should return the relative difference between actual and target value', () => {
    expect(calculateTargetValueHeat({a: 10}, {a: {value: 5, unit: 'millis'}})).toEqual({a: 1});
  });

  it('should return null for an element that is below target value', () => {
    expect(calculateTargetValueHeat({a: 2}, {a: {value: 5, unit: 'millis'}})).toEqual({a: null});
  });
});
