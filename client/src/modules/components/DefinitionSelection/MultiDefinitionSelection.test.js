/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
      name: null,
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
      {key: 'foo', versions: ['all']},
      {key: 'bar', versions: ['all']},
    ],
    props.location.pathname
  );
  expect(spy).toHaveBeenCalledWith([
    {
      identifier: 'randomID',
      key: 'foo',
      name: 'Foo',
      tenantIds: ['a', 'b'],
      versions: ['all'],
    },
    {
      identifier: 'randomID',
      key: 'bar',
      name: null,
      tenantIds: ['a'],
      versions: ['all'],
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

it('should display key of definition if name is null', () => {
  const node = shallow(<MultiDefinitionSelection {...props} />);

  expect(node.find({value: 'bar'}).text()).toBe('bar');
});
