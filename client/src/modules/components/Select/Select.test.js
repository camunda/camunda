/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import Select from './Select';

it('should render without crashing', () => {
  shallow(<Select />);
});

it('should render a .Select className by default', () => {
  const node = shallow(<Select />);

  expect(node).toMatchSelector('.Select');
});

it('should merge and render additional classNames as provided as a property', () => {
  const node = shallow(<Select className="foo" />);

  expect(node).toMatchSelector('.Select.foo');
});

it('should render child elements and their props', () => {
  const node = shallow(
    <Select>
      <Select.Option id="test_option" value="1">
        Option One
      </Select.Option>
    </Select>
  );

  expect(node.find('#test_option')).toExist();
  expect(node.find('Option[value="1"]')).toExist();
});

it('should select option onClick and add checked property', () => {
  const spy = jest.fn();
  const node = shallow(
    <Select onChange={spy}>
      <Select.Option value="1">Option One</Select.Option>
    </Select>
  );

  node.find('Option').simulate('click', {target: {getAttribute: () => '1'}});
  expect(spy).toHaveBeenCalledWith('1');

  node.setProps({value: '1'});

  expect(node.find('Option').props('checked')).toBeTruthy();
  expect(node.find('Dropdown').prop('label')).toBe('Option One');
});

it('should select submenu option onClick and set checked property on the submenu and the option', () => {
  const spy = jest.fn();
  const node = shallow(
    <Select onChange={spy}>
      <Select.Submenu label="submenu">
        <Select.Option value="1">Option One</Select.Option>
      </Select.Submenu>
    </Select>
  );

  node.find('Option').simulate('click', {target: {getAttribute: () => '1'}});
  expect(spy).toHaveBeenCalledWith('1');

  node.setProps({value: '1'});

  expect(node.find('Submenu').props().checked).toBeTruthy();
  expect(node.find('Option').props().checked).toBeTruthy();
  expect(node.find('Dropdown').prop('label')).toBe('submenu : Option One');
});
