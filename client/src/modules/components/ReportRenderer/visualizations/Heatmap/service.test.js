/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {calculateTargetValueHeat} from './service';

jest.mock('heatmap.js', () => {});
jest.mock('services', () => ({
  formatters: {convertToMilliseconds: value => value}
}));

describe('calculateTargetValueHeat', () => {
  it('should return the relative difference between actual and target value', () => {
    expect(calculateTargetValueHeat({a: 10}, {a: {value: 5, unit: 'millis'}})).toEqual({a: 1});
  });

  it('should return null for an element that is below target value', () => {
    expect(calculateTargetValueHeat({a: 2}, {a: {value: 5, unit: 'millis'}})).toEqual({a: null});
  });
});
