/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';

import NumberInput from './NumberInput';

import {shallow} from 'enzyme';

const props = {
  filter: NumberInput.defaultFilter,
  setValid: jest.fn(),
  changeFilter: jest.fn(),
};

beforeEach(() => {
  props.changeFilter.mockClear();
});

it('should remove all values except the first one if operator is "is less/greater than"', () => {
  const node = shallow(
    <NumberInput
      {...props}
      filter={{operator: 'in', values: ['123', '12', '17'], includeUndefined: false}}
    />
  );

  node.instance().setOperator('<');
  expect(props.changeFilter).toHaveBeenCalledWith({
    operator: '<',
    values: ['123'],
    includeUndefined: false,
  });
});

it('should not show the add value button for greater and less than operators', () => {
  const node = shallow(<NumberInput {...props} filter={{operator: '<', values: ['']}} />);

  expect(node.find('ValueListInput').prop('allowMultiple')).toBe(false);
});

it('should parse an existing filter', () => {
  const parsed = NumberInput.parseFilter({data: {data: {values: ['123', null], operator: 'in'}}});

  expect(parsed).toEqual({operator: 'in', values: ['123'], includeUndefined: true});
});

it('should convert includeUndefined to a null entry in filter values', () => {
  const spy = jest.fn();
  NumberInput.addFilter(
    spy,
    'variable',
    {name: 'aVariableName', type: 'long'},
    {operator: 'in', values: ['123'], includeUndefined: true},
    {identifier: 'definition'}
  );

  expect(spy).toHaveBeenCalledWith({
    data: {data: {operator: 'in', values: ['123', null]}, name: 'aVariableName', type: 'long'},
    type: 'variable',
    appliedTo: ['definition'],
  });
});

describe('NumberInput.isValid', () => {
  it('should return true for valid filters', () => {
    let result = NumberInput.isValid({values: ['123']});

    expect(result).toBe(true);

    result = NumberInput.isValid({values: [], includeUndefined: true});

    expect(result).toBe(true);
  });

  it('should return false for invalid filters', () => {
    let result = NumberInput.isValid({values: ['NaN']});

    expect(result).toBe(false);

    result = NumberInput.isValid({values: [], includeUndefined: false});

    expect(result).toBe(false);
  });
});
