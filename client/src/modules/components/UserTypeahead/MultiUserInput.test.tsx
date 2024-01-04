/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';

import MultiUserInput from './MultiUserInput';
import {searchIdentities} from './service';

import {FilterableMultiSelect} from '@carbon/react';

jest.mock('debouncePromise', () => () => (fn: any) => fn());

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
      {id: 'testUser', type: 'user'},
      {id: 'user2', email: 'testUser@test.com', type: 'user'},
      {id: 'groupId', name: 'groupName', email: 'group@test.com', type: 'group'},
    ],
    total: 50,
  });

  const node = shallow(
    <MultiUserInput
      {...props}
      users={[
        {
          id: 'GROUP:groupId',
          identity: {id: 'groupId', name: 'groupName', type: 'group', email: ''},
        },
      ]}
    />
  );

  runAllEffects();
  await flushPromises();

  const items = node.find(FilterableMultiSelect).prop('items');

  const content = node.find(FilterableMultiSelect).renderProp('itemToElement')?.(items[0]);
  expect(content).toIncludeText('groupName (User group)');
  expect(content.find('.subText')).toIncludeText('groupId');

  const item2Content = node.find(FilterableMultiSelect).renderProp('itemToElement')?.(items[1]);
  expect(item2Content.text()).toBe('testUser');

  const item3Content = node.find(FilterableMultiSelect).renderProp('itemToElement')?.(items[2]);
  expect(item3Content).toIncludeText('testUser@test.com');
  expect(item3Content.find('.subText')).toIncludeText('user2');
});

it('should invoke onAdd & onRemove when selecting/deselecting an identity', async () => {
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

  node.find(FilterableMultiSelect).prop('downshiftProps')?.onSelect(items[0]);
  expect(spy).toHaveBeenCalledWith(testUser);
});

it('should invoke onRemove when deselecting an identity', async () => {
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
  const node = shallow(
    <MultiUserInput
      {...props}
      onRemove={spy}
      users={[
        {
          id: 'USER:test',
          identity: testUser,
        },
      ]}
    />
  );

  runAllEffects();
  await flushPromises();

  const items = node.find(FilterableMultiSelect).prop('items');

  node.find(FilterableMultiSelect).prop('downshiftProps')?.onSelect(items[0]);
  expect(spy).toHaveBeenCalledWith('USER:test');
});

it('should invoke onAdd when selecting an identity even if it is not in loaded identities', async () => {
  const spy = jest.fn();
  const node = shallow(<MultiUserInput {...props} onAdd={spy} />);

  runAllEffects();
  await flushPromises();

  node.find(FilterableMultiSelect).prop('downshiftProps')?.onSelect({id: 'test', label: 'test'});
  expect(spy).toHaveBeenCalledWith({id: 'test'});
});
