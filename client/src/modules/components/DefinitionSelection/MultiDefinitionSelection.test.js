/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {MultiSelect} from 'components';

import {loadTenants} from './service';
import {MultiDefinitionSelection} from './MultiDefinitionSelection';

jest.mock('./service', () => ({
  loadTenants: jest.fn().mockReturnValue([
    {
      tenants: [
        {
          id: 'a',
          name: 'Tenant A',
        },
        {
          id: 'b',
          name: 'Tenant B',
        },
      ],
    },
    {
      tenants: [
        {
          id: 'a',
          name: 'Tenant A',
        },
      ],
    },
  ]),
}));

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  getRandomId: () => 'randomID',
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  availableDefinitions: [
    {
      key: 'foo',
      name: 'Foo',
    },
    {
      key: 'bar',
      name: 'Bar',
    },
  ],
  selectedDefinitions: [],
  onChange: jest.fn(),
  resetSelection: jest.fn(),
  location: {pathname: null},
};

it('should invoke changeDefinition when only one definition is selected', () => {
  const spy = jest.fn();
  const node = shallow(<MultiDefinitionSelection {...props} changeDefinition={spy} />);

  node.find(MultiSelect).simulate('add', 'foo');

  expect(spy).toHaveBeenCalledWith('foo');
});

it('should invoke loadTenants and onChange when selecting more than one definition', () => {
  const spy = jest.fn();
  const node = shallow(
    <MultiDefinitionSelection
      {...props}
      selectedDefinitions={[props.availableDefinitions[0]]}
      onChange={spy}
    />
  );

  node.find(MultiSelect).simulate('add', 'bar');
  expect(loadTenants).toHaveBeenCalledWith(
    'process',
    [
      {key: 'foo', versions: ['latest']},
      {key: 'bar', versions: ['latest']},
    ],
    props.location.pathname
  );
  expect(spy).toHaveBeenCalledWith([
    {
      displayName: 'Foo',
      identifier: 'randomID',
      key: 'foo',
      name: 'Foo',
      tenantIds: ['a', 'b'],
      versions: ['latest'],
    },
    {
      displayName: 'Bar',
      identifier: 'randomID',
      key: 'bar',
      name: 'Bar',
      tenantIds: ['a'],
      versions: ['latest'],
    },
  ]);
});

it('should invoke onChange when removing definition', () => {
  const spy = jest.fn();
  const node = shallow(
    <MultiDefinitionSelection
      {...props}
      selectedDefinitions={[props.availableDefinitions[0]]}
      onChange={spy}
    />
  );

  node.find(MultiSelect).simulate('remove', 'foo');
  expect(spy).toHaveBeenCalledWith([]);
});
