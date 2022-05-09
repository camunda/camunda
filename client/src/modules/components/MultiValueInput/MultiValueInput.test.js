/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import MultiValueInput from './MultiValueInput';

import UncontrolledMultiValueInput from './UncontrolledMultiValueInput';

it('should invoke onRemove when removing a value', async () => {
  const spy = jest.fn();
  const node = shallow(
    <MultiValueInput values={[{value: 'test1'}, {value: 'test2'}]} onRemove={spy} />
  );

  node.find(UncontrolledMultiValueInput).simulate('keyDown', {
    key: 'Backspace',
  });

  expect(spy).toHaveBeenCalledWith('test2', 1);
});

it('should invoke onAdd when adding a value', async () => {
  const spy = jest.fn();
  const node = shallow(<MultiValueInput values={[]} onAdd={spy} extraSeperators={[';']} />);

  node.find(UncontrolledMultiValueInput).prop('onChange')({target: {value: 'test1'}});
  node.find(UncontrolledMultiValueInput).simulate('keyDown', {
    key: ';',
    preventDefault: jest.fn(),
  });
  expect(spy).toHaveBeenCalledWith('test1');

  spy.mockClear();

  node.find(UncontrolledMultiValueInput).prop('onChange')({target: {value: 'test2'}});
  node.find(UncontrolledMultiValueInput).simulate('blur');

  expect(spy).toHaveBeenCalledWith('test2');
});
