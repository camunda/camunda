/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {Button} from 'components';

import TenantPopover from './TenantPopover';

const tenants = [
  {id: 'a', name: 'Tenant A'},
  {id: 'b', name: 'Tenant B'},
  {id: 'c', name: 'Tenant C'},
  {id: null, name: 'Not defined'},
];

it('should call the provided onChange function', () => {
  const spy = jest.fn();
  const node = shallow(<TenantPopover onChange={spy} tenants={tenants} selected={[null]} />);

  node
    .find('Switch')
    .first()
    .simulate('change', {target: {checked: true}});

  expect(spy).toHaveBeenCalledWith([null, 'a']);

  node.find(Button).first().simulate('click');

  expect(spy).toHaveBeenCalledWith(['a', 'b', 'c', null]);

  node.find(Button).last().simulate('click');

  expect(spy).toHaveBeenCalledWith([]);
});

it('should construct the label based on the selected tenants', () => {
  const node = shallow(<TenantPopover tenants={tenants} selected={[]} />);

  expect(node.find('.TenantPopover').prop('title')).toBe('Select...');

  node.setProps({selected: ['a']});

  expect(node.find('.TenantPopover').prop('title')).toBe('Tenant A');

  node.setProps({selected: ['a', 'b']});

  expect(node.find('.TenantPopover').prop('title')).toBe('Multiple');

  node.setProps({selected: ['a', 'b', 'c', null]});

  expect(node.find('.TenantPopover').prop('title')).toBe('All');

  node.setProps({tenants: [{id: null, name: 'Not defined'}], selected: [null]});

  expect(node.find('.TenantPopover').prop('title')).toBe('Not defined');
  expect(node.find('.TenantPopover')).toBeDisabled();
});

it('should not crash, but be disabled if no tenants are provided', () => {
  const node = shallow(<TenantPopover selected={[]} />);
  expect(node.find('.TenantPopover').prop('disabled')).toBe(true);
});

it('should allow manual disabling', () => {
  const node = shallow(<TenantPopover tenants={tenants} selected={[null]} disabled />);
  expect(node.find('.TenantPopover').prop('disabled')).toBe(true);
});

it('should diplay a loading indicator and disable the switches while loading', () => {
  const node = shallow(<TenantPopover tenants={tenants} selected={[null]} loading />);

  expect(node.find('LoadingIndicator')).toExist();
  expect(node.find('Switch').at(0).prop('disabled')).toBe(true);
});
