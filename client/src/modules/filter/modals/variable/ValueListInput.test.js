/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import ValueListInput from './ValueListInput';
import {LabeledInput, MultiValueInput} from 'components';

import {shallow} from 'enzyme';

const props = {
  filter: {operator: 'in', values: [], includeUndefined: false},
  allowMultiple: true,
  allowUndefined: true,
  onChange: jest.fn(),
};

beforeEach(() => {
  props.onChange.mockClear();
});

it('should add a new value to the filter using the single input', () => {
  const node = shallow(<ValueListInput {...props} allowMultiple={false} />);

  node.find('.singeValueInput').prop('onChange')({target: {value: 'newValue'}});

  expect(props.onChange).toHaveBeenCalledWith({
    includeUndefined: false,
    operator: 'in',
    values: ['newValue'],
  });
});

it('should add a new a value to the filter using the multi value input', () => {
  const node = shallow(<ValueListInput {...props} filter={{operator: 'in', values: ['1']}} />);

  node.find(MultiValueInput).prop('onAdd')('newValue');

  expect(props.onChange).toHaveBeenCalledWith({
    operator: 'in',
    values: ['1', 'newValue'],
  });
});

it('should remove a value from the filter', () => {
  const node = shallow(<ValueListInput {...props} filter={{operator: 'in', values: ['1', '2']}} />);

  node.find(MultiValueInput).prop('onRemove')('1', 0);

  expect(props.onChange).toHaveBeenCalledWith({
    operator: 'in',
    values: ['2'],
  });
});

it('should add multiple values at once using multiValueInput paste', () => {
  const node = shallow(<ValueListInput {...props} />);

  node.find(MultiValueInput).simulate('paste', {
    preventDefault: jest.fn(),
    clipboardData: {getData: () => `value1 value.2  value3`},
  });

  expect(props.onChange).toHaveBeenCalledWith({
    includeUndefined: false,
    operator: 'in',
    values: ['value1', 'value.2', 'value3'],
  });
});

it('use the passed isValid function to validate passed values to multiValueInput', () => {
  const node = shallow(
    <ValueListInput
      {...props}
      filter={{operator: 'in', values: [0, 1, 2]}}
      isValid={(val) => (val < 2 ? true : false)}
    />
  );

  expect(node.find(MultiValueInput).prop('values')).toEqual([
    {invalid: false, value: 0},
    {invalid: false, value: 1},
    {invalid: true, value: 2},
  ]);
});

it('should set includeUndefined to true when enabling the checkbox"', () => {
  const filter = {operator: 'in', values: ['123', '12', '17'], includeUndefined: false};
  const node = shallow(<ValueListInput {...props} filter={filter} />);

  node.find(LabeledInput).prop('onChange')({target: {checked: true}});
  expect(props.onChange).toHaveBeenCalledWith({...filter, includeUndefined: true});
});

it('should add a provided className', () => {
  const node = shallow(<ValueListInput {...props} className="anotherClass" />);

  expect(node).toHaveClassName('anotherClass');
});

it('should not show undefined option if allowUndefined is not set', () => {
  const node = shallow(<ValueListInput {...props} allowUndefined={undefined} />);

  expect(node.find('.undefinedOption')).not.toExist();
});

it('should not allow adding another value if allowMultiple is not set', () => {
  const node = shallow(<ValueListInput {...props} allowMultiple={undefined} />);

  expect(node.find('.addValueButton')).not.toExist();
});
