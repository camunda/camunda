/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {getOptimizeProfile} from 'config';

import DefinitionEditor from './DefinitionEditor';
import {DefinitionList} from './DefinitionList';
import {loadTenants} from './service';

jest.mock('config', () => ({
  getOptimizeProfile: jest.fn().mockReturnValue('platform'),
  areTenantsAvailable: jest.fn().mockReturnValue(true),
}));

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn(() => ({
    mightFail: jest.fn((data, cb) => cb(data)),
  })),
  useDocs: jest.fn(() => ({
    generateDocsLink: jest.fn((url) => url),
  })),
}));

jest.mock('./service', () => ({
  loadTenants: jest.fn().mockReturnValue([
    {
      key: 'definitionA',
      versions: ['all'],
      tenants: [
        {id: 'a', name: 'Tenant A'},
        {id: 'b', name: 'Tenant B'},
        {id: 'c', name: 'Tenant C'},
      ],
    },
  ]),
}));

const props = {
  mightFail: (data, cb) => cb(data),
  location: '',
  type: 'process',
  definitions: [
    {
      key: 'definitionA',
      name: 'Definition A',
      displayName: 'Definition A',
      versions: ['all'],
      tenantIds: ['a', 'b'],
    },
  ],
};

it('should show a list of added definitions', () => {
  const node = shallow(<DefinitionList {...props} />);

  runAllEffects();

  expect(node.find('li').length).toBe(1);
  expect(node.find(DefinitionEditor).prop('definition')).toEqual(props.definitions[0]);
});

it('should display names of tenants', async () => {
  const node = shallow(<DefinitionList {...props} />);

  runAllEffects();

  expect(node.find('.info').at(1).text()).toBe('Tenant: Tenant A, Tenant B');
});

it('should show the only tenant in self managed mode', async () => {
  loadTenants.mockReturnValueOnce([
    {
      key: 'definitionA',
      versions: ['all'],
      tenants: [{id: '<defaut>', name: 'Default'}],
    },
  ]);
  getOptimizeProfile.mockReturnValueOnce('ccsm');
  const node = shallow(
    <DefinitionList
      {...props}
      definitions={[
        {
          key: 'definitionA',
          name: 'Definition A',
          displayName: 'Definition A',
          versions: ['all'],
          tenantIds: ['<defaut>'],
        },
      ]}
    />
  );

  runAllEffects();
  await flushPromises();
  expect(node.find('.info').at(1).text()).toBe('Tenant: Default');
});

it('should allow copying definitions', () => {
  const spy = jest.fn();
  const node = shallow(<DefinitionList {...props} onCopy={spy} />);

  node.find('.actionBtn').first().simulate('click');
  expect(spy).toHaveBeenCalled();
});

it('should not allow copy if limit of 10 definitions is reached', () => {
  const node = shallow(
    <DefinitionList
      {...props}
      definitions={Array(10).fill({
        key: 'definitionA',
        name: 'Definition A',
        displayName: 'Definition A',
        versions: ['all'],
        tenantIds: ['a', 'b'],
      })}
    />
  );

  expect(node.find({iconDescription: 'Copy'})).not.toExist();
});
