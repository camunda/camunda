/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

  node.instance().setOperator('<')({preventDefault: () => null});
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

it('should disable add filter button if provided value is invalid', () => {
  const spy = jest.fn();
  const node = shallow(<NumberInput {...props} setValid={spy} />);

  node.setProps({filter: {operator: 'in', values: ['123xxxx'], includeUndefined: false}});

  expect(spy).toHaveBeenCalledWith(false);
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
