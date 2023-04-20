/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import MultiUserInput from './MultiUserInput';
import {searchIdentities} from './service';

jest.mock('debouncePromise', () => () => (fn: any) => fn());

jest.mock('./service', () => ({
  searchIdentities: jest.fn().mockReturnValue({result: [], total: 50}),
}));

const props = {
  users: [],
  onAdd: jest.fn(),
  onRemove: jest.fn(),
  onClear: jest.fn(),
};

it('should render a MultiSelect', () => {
  const node = shallow(<MultiUserInput {...props} />);

  expect(node.find('MultiSelect')).toExist();
});

it('should load initial data when select is opened', async () => {
  const node = shallow(<MultiUserInput {...props} />);

  node.find('MultiSelect').simulate('open');
  await flushPromises();

  expect(searchIdentities).toHaveBeenCalled();
});

it('should enable loading while loading data and enable hasMore if there are more data available', async () => {
  const node = shallow(<MultiUserInput {...props} />);

  node.find('MultiSelect').simulate('open');

  expect(node.find('MultiSelect').prop('loading')).toBe(true);
  await flushPromises();
  expect(node.find('MultiSelect').prop('loading')).toBe(false);
  expect(node.find('MultiSelect').prop('hasMore')).toBe(true);
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
  node.find('MultiSelect').simulate('open');
  await flushPromises();

  expect(node).toMatchSnapshot();
});

it('should invoke onAdd when selecting an identity even if it is not in loaded identities', async () => {
  (searchIdentities as jest.Mock).mockReturnValue({result: [{id: 'notTest'}], total: 1});
  const spy = jest.fn();
  const node = shallow(<MultiUserInput {...props} onAdd={spy} />);

  node.find('MultiSelect').simulate('add', 'test');

  expect(spy).toHaveBeenCalledWith({id: 'test'});
});
