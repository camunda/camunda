/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

it('should close the menu after selecting an option if persistMenu option is set false', () => {
  const spy = jest.fn();
  const node = shallow(
    <MultiSelect onClose={spy} onAdd={() => {}}>
      <MultiSelect.Option id="test_option" value="1">
        Option One
      </MultiSelect.Option>
    </MultiSelect>
  );

  node
    .find('OptionsList')
    .props()
    .onSelect({props: {children: 'Option One', value: '1'}});
  expect(spy).not.toHaveBeenCalled();

  node.setProps({persistMenu: false});
  node
    .find('OptionsList')
    .props()
    .onSelect({props: {children: 'Option One', value: '1'}});

  expect(spy).toHaveBeenCalled();
});

it('clear the search after selecting an option', () => {
  const searchSpy = jest.fn();
  const node = shallow(
    <MultiSelect onSearch={searchSpy} onAdd={() => {}}>
      <MultiSelect.Option id="test_option" value="1">
        Option One
      </MultiSelect.Option>
    </MultiSelect>
  );

  node.find(UncontrolledMultiValueInput).simulate('change', {target: {value: 'one'}});
  node
    .find('OptionsList')
    .props()
    .onSelect({props: {children: 'Option One', value: '1'}});

  expect(searchSpy).toHaveBeenCalledWith('');
});
