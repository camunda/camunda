/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import MultiUserInput from './MultiUserInput';
import {searchIdentities} from './service';

jest.mock('debounce', () =>
  jest.fn((fn) => {
    fn.clear = jest.fn();
    return fn;
  })
);

jest.mock('./service', () => ({
  searchIdentities: jest.fn().mockReturnValue({result: [], total: 50}),
}));

it('should render a MultiSelect', () => {
  const node = shallow(<MultiUserInput />);

  expect(node.find('MultiSelect')).toExist();
});

it('should load initial data when component is mounted', () => {
  shallow(<MultiUserInput />);

  runLastEffect();

  expect(searchIdentities).toHaveBeenCalledWith('');
});

it('should enable loading while loading data and enable hasMore if there are more data available', async () => {
  const node = shallow(<MultiUserInput />);
  runLastEffect();

  expect(node.find('MultiSelect').prop('loading')).toBe(true);
  await node.update();
  expect(node.find('MultiSelect').prop('loading')).toBe(false);
  expect(node.find('MultiSelect').prop('hasMore')).toBe(true);
});

it('should enable loading if typeahead is closed while empty', async () => {
  const node = shallow(<MultiUserInput />);

  node.find('MultiSelect').prop('onSearch')('test');
  await node.update();
  node.find('MultiSelect').prop('onClose')();
  expect(node.find('MultiSelect').prop('loading')).toBe(true);
});

it('should format user list information correctly', async () => {
  searchIdentities.mockReturnValueOnce({
    result: [
      {id: 'testUser', type: 'user'},
      {id: 'user2', email: 'testUser@test.com', type: 'user'},
      {id: 'groupId', name: 'groupName', email: 'group@test.com', type: 'group'},
    ],
    total: 50,
  });
  const node = shallow(
    <MultiUserInput
      users={[{id: 'GROUP:groupId', identity: {id: 'groupId', name: 'groupName', type: 'group'}}]}
    />
  );
  runLastEffect();
  await node.update();

  expect(node).toMatchSnapshot();
});

it('should invoke onAdd when selecting an identity even if it is not in loaded identities', async () => {
  searchIdentities.mockReturnValue({result: [{id: 'notTest'}], total: 1});
  const spy = jest.fn();
  const node = shallow(<MultiUserInput onAdd={spy} />);

  node.find('MultiSelect').prop('onAdd')('test');

  expect(spy).toHaveBeenCalledWith({id: 'test'});
});
