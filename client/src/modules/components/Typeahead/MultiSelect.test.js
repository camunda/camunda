/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import MultiSelect from './MultiSelect';
import {UncontrolledMultiValueInput} from 'components';

import {shallow} from 'enzyme';

it('should show/hide options list on input focus/blur', () => {
  const node = shallow(
    <MultiSelect>
      <MultiSelect.Option id="test_option" value="1">
        Option One
      </MultiSelect.Option>
    </MultiSelect>
  );

  node.find(UncontrolledMultiValueInput).simulate('focus');

  expect(node.find('OptionsList').prop('open')).toBe(true);

  node.find(UncontrolledMultiValueInput).simulate('blur');

  expect(node.find('OptionsList').prop('open')).toBe(false);
});

it('should add a selected option', () => {
  const spy = jest.fn();
  const node = shallow(
    <MultiSelect onAdd={spy}>
      <MultiSelect.Option id="test_option" value="1">
        Option One
      </MultiSelect.Option>
    </MultiSelect>
  );

  node
    .find('OptionsList')
    .props()
    .onSelect({props: {children: 'Option One', value: '1'}});

  expect(spy).toHaveBeenCalledWith('1');
});

it('should reset MultiSelect when clicking outside', async () => {
  const node = shallow(
    <MultiSelect values={[{value: 'test', label: 'test label'}]}>
      <MultiSelect.Option id="test_option" value="1">
        Option One
      </MultiSelect.Option>
    </MultiSelect>
  );

  node.find(UncontrolledMultiValueInput).prop('onChange')({target: {value: 'randomText'}});
  expect(node.find('OptionsList').prop('filter')).toBe('randomText');

  node.find(UncontrolledMultiValueInput).simulate('blur');

  expect(node.find('OptionsList').prop('filter')).toBe('');
});
