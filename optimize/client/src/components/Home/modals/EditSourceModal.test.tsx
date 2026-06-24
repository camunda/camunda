/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from '__mocks__/react';
import {ComponentProps} from 'react';
import {shallow} from 'enzyme';

import {UNAUTHORIZED_TENANT_ID} from 'services';

import EditSourceModal from './EditSourceModal';
import {getDefinitionTenants} from './service';

jest.mock('./service', () => ({getDefinitionTenants: jest.fn()}));
jest.mock('hooks', () => ({
  useErrorHandling: () => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  }),
}));

const props: ComponentProps<typeof EditSourceModal> = {
  onClose: jest.fn(),
  onConfirm: jest.fn(),
  source: {
    definitionKey: 'defKey',
    definitionName: 'defName',
    definitionType: 'process',
    tenants: [
      {id: null, name: 'Not defined'},
      {id: UNAUTHORIZED_TENANT_ID, name: 'unauthorizedTenant'},
    ],
  },
};

(getDefinitionTenants as jest.Mock).mockReturnValue({
  ...props.source,
  tenants: [
    {id: null, name: 'Not defined'},
    {id: 'test', name: 'testName'},
  ],
});

it('should match snapshot', () => {
  const node = shallow(<EditSourceModal {...props} />);

  runAllEffects();

  expect(node).toMatchSnapshot();
});

it('should get defintion tenants on mount', () => {
  shallow(<EditSourceModal {...props} />);

  runAllEffects();

  expect(getDefinitionTenants).toHaveBeenCalled();
});

it('should update selected tenants on itemList change', () => {
  const node = shallow(<EditSourceModal {...props} />);

  node.find('Checklist').simulate('change', [{id: null, name: 'Not defined'}]);
  node.find('.confirm').simulate('click');

  expect(props.onConfirm).toHaveBeenCalledWith([null]);
});

it('should not deselect unauthorized tenants', () => {
  const node = shallow(<EditSourceModal {...props} />);

  node.find('Checklist').simulate('change', []);

  expect(node.find('Checklist').prop('selectedItems')).toEqual([
    {id: UNAUTHORIZED_TENANT_ID, name: 'unauthorizedTenant'},
  ]);
});
