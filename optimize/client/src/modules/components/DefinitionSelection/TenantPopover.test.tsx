/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';
import {Button} from '@carbon/react';

import TenantPopover from './TenantPopover';

const tenants = [
  {id: 'a', name: 'Tenant A'},
  {id: 'b', name: 'Tenant B'},
  {id: 'c', name: 'Tenant C'},
  {id: null, name: 'Not defined'},
];

const props = {
  onChange: jest.fn(),
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('should call the provided onChange function', () => {
  const node = shallow(<TenantPopover {...props} tenants={tenants} selected={[null]} />);

  node.find('Toggle').first().simulate('toggle', true);

  expect(props.onChange).toHaveBeenCalledWith([null, 'a']);

  node.find(Button).first().simulate('click');

  expect(props.onChange).toHaveBeenCalledWith(['a', 'b', 'c', null]);

  node.find(Button).last().simulate('click');

  expect(props.onChange).toHaveBeenCalledWith([]);
});

it('should construct the label based on the selected tenants', () => {
  const node = shallow(<TenantPopover {...props} tenants={tenants} selected={[]} />);

  expect(node.prop('trigger').props.children).toBe('Select...');

  node.setProps({selected: ['a']});

  expect(node.prop('trigger').props.children).toBe('Tenant A');

  node.setProps({selected: ['a', 'b']});

  expect(node.prop('trigger').props.children).toBe('Multiple');

  node.setProps({selected: ['a', 'b', 'c', null]});

  expect(node.prop('trigger').props.children).toBe('All');

  node.setProps({tenants: [{id: null, name: 'Not defined'}], selected: [null]});

  expect(node.prop('trigger').props.children).toBe('Not defined');
  expect(node.prop('trigger').props.disabled).toBe(true);
});

it('should not crash, but be disabled if no tenants are provided', () => {
  const node = shallow(<TenantPopover {...props} selected={[]} tenants={[]} />);
  expect(node.prop('trigger').props.disabled).toBe(true);
});

it('should allow manual disabling', () => {
  const node = shallow(<TenantPopover {...props} tenants={tenants} selected={[null]} disabled />);
  expect(node.prop('trigger').props.disabled).toBe(true);
});

it('should diplay a loading indicator and disable the switches while loading', () => {
  const node = shallow(<TenantPopover {...props} tenants={tenants} selected={[null]} loading />);

  expect(node.find('Loading')).toExist();
  expect(node.find('Toggle').at(0).prop('disabled')).toBe(true);
});
