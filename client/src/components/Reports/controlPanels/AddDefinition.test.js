/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {Button, Modal} from 'components';

import {AddDefinition} from './AddDefinition';
import {loadDefinitions, loadTenants} from './service';

jest.mock('./service', () => ({
  loadDefinitions: jest.fn().mockReturnValue([
    {key: 'definitionA', name: 'Definition A'},
    {key: 'definitionB', name: 'Definition B'},
    {key: 'definitionA2', name: 'Definition A'},
  ]),
  loadTenants: jest.fn().mockReturnValue(),
}));

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  getRandomId: () => 'randomID',
}));

const props = {
  mightFail: (data, cb) => cb(data),
  location: {pathname: ''},
  type: 'process',
  definitions: [],
};

it('should open a modal on button click', () => {
  const node = shallow(<AddDefinition {...props} />);

  node.find(Button).simulate('click', {stopPropagation: () => {}});

  expect(node.find(Modal).prop('open')).toBe(true);
});

it('should contain a checklist with all available definitions', () => {
  const node = shallow(<AddDefinition {...props} />);
  runLastEffect();

  expect(node.find('Checklist').prop('allItems')).toEqual(loadDefinitions());
});

it('should disable already added definitions', () => {
  const node = shallow(<AddDefinition {...props} definitions={[{key: 'definitionB'}]} />);
  runLastEffect();

  expect(
    node
      .find('Checklist')
      .prop('formatter')()
      .find(({id}) => id === 'definitionB').disabled
  ).toBe(true);
});

it('should show the definition key if the name is not unique', () => {
  const node = shallow(<AddDefinition {...props} definitions={[{key: 'definitionB'}]} />);
  runLastEffect();

  const formattedEntries = node.find('Checklist').prop('formatter')();

  expect(formattedEntries[0].label).toBe('Definition A (definitionA)');
  expect(formattedEntries[2].label).toBe('Definition A (definitionA2)');
});

it('should show the definition key if the name is null ', () => {
  loadDefinitions.mockReturnValueOnce([
    {key: 'key0', name: 'name0'},
    {key: 'key1', name: null},
  ]);
  const node = shallow(
    <AddDefinition {...props} definitions={[{key: 'testDefKey', name: null}]} />
  );
  runLastEffect();

  const formattedEntries = node.find('Checklist').prop('formatter')();

  expect(formattedEntries[0].label).toBe('name0');
  expect(formattedEntries[1].label).toBe('key1');
});

it('should call back with definitions to add', () => {
  const spy = jest.fn();
  const node = shallow(
    <AddDefinition {...props} definitions={[{key: 'definitionB'}]} onAdd={spy} />
  );
  runLastEffect();

  node.find('Checklist').simulate('change', [node.find('Checklist').prop('allItems')[0]]);

  loadTenants.mockReturnValueOnce([
    {
      key: 'definitionA',
      versions: ['all'],
      tenants: [{id: null, name: 'Not Defined'}],
    },
  ]);
  node.find('.confirm').simulate('click', {stopPropagation: () => {}});

  expect(spy).toHaveBeenCalledWith([
    {
      key: 'definitionA',
      name: 'Definition A',
      displayName: 'Definition A',
      versions: ['all'],
      tenantIds: [null],
      identifier: 'randomID',
    },
  ]);
});

it('should show a warning if limit of 10 definitions is reached', () => {
  const node = shallow(<AddDefinition {...props} definitions={Array(9).fill({})} />);

  expect(node.find('MessageBox')).not.toExist();

  node.find('Checklist').simulate('change', [{}]);

  expect(node.find('MessageBox')).toExist();
});
