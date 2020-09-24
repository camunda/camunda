/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Input} from 'components';

import MultiValueInput from './MultiValueInput';

it('should match snapshot', () => {
  const node = shallow(
    <MultiValueInput
      values={[
        {value: '1234', invalid: false},
        {value: 'errorValue', invalid: true},
      ]}
    />
  );

  expect(node).toMatchSnapshot();
});

it('should show placeholder when empty', () => {
  const placeholderText = 'placeholderText';
  const node = shallow(<MultiValueInput values={[]} placeholder={placeholderText} />);

  expect(node.find('.placeholder')).toIncludeText(placeholderText);
});

it('should invoke onAdd when adding a value', async () => {
  const spy = jest.fn();
  const node = shallow(<MultiValueInput values={[]} onAdd={spy} extraSeperators={[';']} />);

  node.find(Input).prop('onChange')({target: {value: 'test1'}});
  node.find(Input).simulate('keyDown', {
    key: ';',
    preventDefault: jest.fn(),
  });
  expect(spy).toHaveBeenCalledWith('test1');

  spy.mockClear();

  node.find(Input).prop('onChange')({target: {value: 'test2'}});
  node.find(Input).simulate('blur');

  expect(spy).toHaveBeenCalledWith('test2');
});

it('should invoke onRemove when removing a value', async () => {
  const spy = jest.fn();
  const node = shallow(
    <MultiValueInput values={[{value: 'test1'}, {value: 'test2'}]} onRemove={spy} />
  );

  node.find('Tag').at(0).prop('onRemove')();

  expect(spy).toHaveBeenCalledWith('test1', 0);

  spy.mockClear();

  node.find(Input).simulate('keyDown', {
    key: 'Backspace',
  });

  expect(spy).toHaveBeenCalledWith('test2', 1);
});
