/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import {getDefinitionTenants} from './service';

import EditSourceModalWithErrorHandling from './EditSourceModal';

const EditSourceModal = EditSourceModalWithErrorHandling.WrappedComponent;

jest.mock('./service', () => ({getDefinitionTenants: jest.fn()}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  onClose: jest.fn(),
  onConfirm: jest.fn(),
  source: {
    definitionKey: 'defKey',
    definitionName: 'defName',
    tenants: [
      {id: null, name: 'Not defined'},
      {id: '__unauthorizedTenantId__', name: 'unauthorizedTenant'},
    ],
  },
};

getDefinitionTenants.mockReturnValue({
  ...props.source,
  tenants: [
    {id: null, name: 'Not defined'},
    {id: 'test', name: 'testName'},
  ],
});

it('should match snapshot', () => {
  const node = shallow(<EditSourceModal {...props} />);

  expect(node).toMatchSnapshot();
});

it('should get defintion tenants on mount', () => {
  shallow(<EditSourceModal {...props} />);

  expect(getDefinitionTenants).toHaveBeenCalled();
});

it('should update selected tenants on itemList change', () => {
  const node = shallow(<EditSourceModal {...props} />);

  node
    .find('Checklist')
    .props()
    .onChange([{id: null, name: 'Not defined'}]);

  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith([null]);
});

it('should not deselect unauthorized tenants', () => {
  const node = shallow(<EditSourceModal {...props} />);

  node.find('Checklist').props().onChange([]);

  expect(node.find('Checklist').props().selectedItems).toEqual([
    {id: '__unauthorizedTenantId__', name: 'unauthorizedTenant'},
  ]);
});
