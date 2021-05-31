/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import DefinitionEditor from './DefinitionEditor';
import {DefinitionList} from './DefinitionList';

jest.mock('./service', () => ({
  loadTenants: jest.fn().mockReturnValue([
    {
      key: 'definitionA',
      versions: ['latest'],
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
      versions: ['latest'],
      tenantIds: ['a', 'b'],
    },
  ],
};

it('should show a list of added definitions', () => {
  const node = shallow(<DefinitionList {...props} />);

  expect(node.find('li').length).toBe(1);
  expect(node.find(DefinitionEditor).prop('definition')).toEqual(props.definitions[0]);
});

it('should display names of tenants', () => {
  const node = shallow(<DefinitionList {...props} />);
  runLastEffect();

  expect(node.find('.info').at(1).text()).toBe('Tenant: Tenant A, Tenant B');
});
