/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import AddSourceModalWithErrorHandling from './AddSourceModal';
import {getDefinitionsWithTenants, getTenantsWithDefinitions} from './service';
import {Button} from 'components';

const AddSourceModal = AddSourceModalWithErrorHandling.WrappedComponent;

jest.mock('./service', () => ({
  getDefinitionsWithTenants: jest.fn().mockReturnValue([]),
  getTenantsWithDefinitions: jest.fn().mockReturnValue([])
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  open: true,
  onClose: jest.fn(),
  onConfirm: jest.fn()
};

it('should match snapshot', () => {
  const node = shallow(<AddSourceModal {...props} />);

  expect(node).toMatchSnapshot();
});

it('should load definitions and tenants on mount', () => {
  shallow(<AddSourceModal {...props} />);

  expect(getDefinitionsWithTenants).toHaveBeenCalled();
  expect(getTenantsWithDefinitions).toHaveBeenCalled();
});

it('should show TenantsSource when clicking Tenant button', () => {
  const node = shallow(<AddSourceModal {...props} />);

  node
    .find(Button)
    .at(1)
    .simulate('click');
  expect(node.find('TenantSource')).toExist();
});

it('should disable the confirm button on invalid state', () => {
  const node = shallow(<AddSourceModal {...props} />);

  expect(node.find('.confirm').props().disabled).toBe(true);
  node.find('.confirm').simulate('click');
  expect(props.onConfirm).not.toHaveBeenCalled();
});

it('should call the onConfirm prop', () => {
  const node = shallow(<AddSourceModal {...props} />);

  const source = {defintionKey: 'testKey'};

  node
    .find('DefinitionSource')
    .props()
    .onChange(source);

  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith(source);
});
