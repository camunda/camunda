/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {FilterableMultiSelect} from '@carbon/react';
import {shallow} from 'enzyme';

import {loadTenants} from './service';
import MultiDefinitionSelection from './MultiDefinitionSelection';

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
  getCollection: () => 'testCollection',
}));

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useLocation: jest.fn().mockReturnValue({pathname: 'testPath'}),
}));

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn(() => ({
    mightFail: jest.fn((data, cb) => cb(data)),
  })),
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
  changeDefinition: jest.fn(),
};

beforeEach(() => {
  jest.clearAllMocks();
});

it('should invoke changeDefinition when only one definition is selected', () => {
  const node = shallow(
    <MultiDefinitionSelection
      {...props}
      availableDefinitions={[
        {
          key: 'foo',
          name: 'Foo',
        },
      ]}
    />
  );

  const selectedItems = [{id: 'foo', label: 'Foo'}];
  node.find(FilterableMultiSelect).simulate('change', {selectedItems});

  expect(props.changeDefinition).toHaveBeenCalledWith('foo');
});

it('should invoke loadTenants and onChange when selecting more than one definition', () => {
  const node = shallow(<MultiDefinitionSelection {...props} />);
  const selectedItems = [
    {id: 'foo', label: 'Foo'},
    {id: 'bar', label: 'Bar'},
  ];
  node.find(FilterableMultiSelect).simulate('change', {selectedItems});

  expect(loadTenants).toHaveBeenCalledWith(
    'process',
    [
      {key: 'foo', versions: ['all']},
      {key: 'bar', versions: ['all']},
    ],
    'testCollection'
  );
  expect(props.onChange).toHaveBeenCalledWith([
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

it('should display key of definition if name is null', () => {
  const node = shallow(
    <MultiDefinitionSelection
      {...props}
      availableDefinitions={[
        {
          key: 'foo',
          name: null,
        },
      ]}
    />
  );

  const option = node.find(FilterableMultiSelect).prop<{id: string; label: string}[]>('items')[0];
  expect(option?.label).toBe('foo');
});
