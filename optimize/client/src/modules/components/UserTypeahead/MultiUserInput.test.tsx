/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';

import MultiUserInput from './MultiUserInput';
import {searchIdentities} from './service';

import {FilterableMultiSelect} from '@carbon/react';

jest.mock('debouncePromise', () => () => (fn: (...args: unknown[]) => unknown) => fn());

jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  searchIdentities: jest.fn().mockReturnValue({result: [], total: 50}),
}));

const props = {
  users: [],
  onAdd: jest.fn(),
  onRemove: jest.fn(),
  onClear: jest.fn(),
};

it('should load initial data when the select is mounted', async () => {
  shallow(<MultiUserInput {...props} />);

  runAllEffects();

  await flushPromises();

  expect(searchIdentities).toHaveBeenCalled();
});

it('should enable loading while loading data', async () => {
  const loadingItem = {id: 'loading', disabled: true, label: ''};
  const node = shallow(<MultiUserInput {...props} />);

  runAllEffects();

  expect(node.find(FilterableMultiSelect).prop('items')).toEqual([loadingItem]);
  const content = node.find(FilterableMultiSelect).prop('itemToElement')?.(
    loadingItem
  ) as JSX.Element;
  expect(content.props.className).toContain('skeleton');

  await flushPromises();
  expect(node.find(FilterableMultiSelect).prop('items')).not.toEqual([loadingItem]);
});

it('should format user list information correctly', async () => {
  (searchIdentities as jest.Mock).mockReturnValueOnce({
    result: [
      {id: 'testUser', email: 'testUser@test.com'},
      {id: 'user2', email: 'user2@test.com', name: 'user2'},
    ],
    total: 50,
  });

  const node = shallow(
    <MultiUserInput
      {...props}
      users={[
        {
          id: 'user2',
          identity: {id: 'user2', email: 'user2@test.com', name: 'user2'},
        },
        {
          id: 'testUser',
          identity: {id: 'testUser', email: 'testUser@test.com', name: ''},
        },
      ]}
    />
  );

  runAllEffects();
  await flushPromises();

  const items = node.find(FilterableMultiSelect).prop('items');

  const item2Content = node.find(FilterableMultiSelect).renderProp('itemToElement')?.(items[0]);
  // The label should be the name of the user
  // When there is an email and a name, the subText should be an email
  expect(item2Content).toIncludeText('user2');
  expect(item2Content.find('.subText')).toIncludeText('user2@test.com');

  // The label should be the email of the user is no name is present
  // When there is an email, but no name, the subText should not be rendered
  const item3Content = node.find(FilterableMultiSelect).renderProp('itemToElement')?.(items[1]);
  expect(item3Content).toIncludeText('testUser@test.com');
  expect(item3Content.find('.subText')).not.toExist();
});

it('should invoke onAdd when selecting an identity', async () => {
  const testUser = {
    name: 'test',
    type: 'user',
    email: 'test@test.com',
    id: 'test',
  };
  (searchIdentities as jest.Mock).mockReturnValue({
    result: [testUser],
    total: 0,
  });

  const spy = jest.fn();
  const node = shallow(<MultiUserInput {...props} onAdd={spy} />);

  runAllEffects();
  await flushPromises();

  const items = node.find(FilterableMultiSelect).prop('items');

  node.find(FilterableMultiSelect).simulate('change', {selectedItems: items});
  expect(spy).toHaveBeenCalledWith(testUser);
});

it('should invoke onRemove when deselecting an identity', async () => {
  const testUser = {
    name: 'test',
    type: 'user',
    email: 'test@test.com',
    id: 'test',
  };

  const selectedUsers = [
    {
      id: 'USER:test',
      identity: testUser,
    },
    {
      id: 'USER:userToRemove',
      identity: {...testUser, id: 'userToRemove'},
    },
  ];

  (searchIdentities as jest.Mock).mockReturnValue({
    result: [testUser],
    total: 0,
  });

  const spy = jest.fn();
  const node = shallow(<MultiUserInput {...props} onRemove={spy} users={selectedUsers} />);

  runAllEffects();
  await flushPromises();

  node.find(FilterableMultiSelect).simulate('change', {
    selectedItems: [selectedUsers[0]],
  });
  expect(spy).toHaveBeenCalledWith('USER:userToRemove');
});

it('should invoke onAdd when selecting an identity even if it is not in loaded identities', async () => {
  const spy = jest.fn();
  const node = shallow(<MultiUserInput {...props} onAdd={spy} />);

  runAllEffects();
  await flushPromises();

  node.find(FilterableMultiSelect).simulate('change', {
    selectedItems: [{id: 'testUserID'}],
  });

  expect(spy).toHaveBeenCalledWith({id: 'testUserID'});
});

it('should display all info in the item string to ensure correct filtering', () => {
  const items = [
    {id: '1', label: 'John Doe', subText: 'john@example.com'},
    {id: '2', label: '', subText: 'jane@example.com'},
    {id: '3', label: '', subText: ''},
  ];

  const node = shallow(<MultiUserInput {...props} />);

  expect(node.find(FilterableMultiSelect).prop('itemToString')?.(items[0])).toBe(
    'John Doejohn@example.com1'
  );
  expect(node.find(FilterableMultiSelect).prop('itemToString')?.(items[1])).toBe(
    'jane@example.com2'
  );
  expect(node.find(FilterableMultiSelect).prop('itemToString')?.(items[2])).toBe('3');
  expect(node.find(FilterableMultiSelect).prop('itemToString')?.(null)).toBe('');
});
