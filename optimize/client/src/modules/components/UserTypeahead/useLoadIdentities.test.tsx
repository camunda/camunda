/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import useLoadIdentities from './useLoadIdentities';
import {Identity, searchIdentities} from './service';

jest.mock('debouncePromise', () => {
  return jest.fn(
    (...args) =>
      (fn: (...args: unknown[]) => void) =>
        fn(...args)
  );
});

const mockIdentities: Identity[] = [
  {id: '1', name: 'John', email: 'john@example.com'},
  {id: '2', name: 'Jane', email: 'jane@example.com'},
];

const mockFetchUsers = jest.fn().mockResolvedValue({
  result: mockIdentities,
});

jest.mock('./service', () => ({
  searchIdentities: jest.fn(() => ({
    result: mockIdentities,
  })),
}));

function Mock({
  excludeGroups,
  fetchUsers,
}: {
  excludeGroups: boolean;
  fetchUsers?: (
    query: string,
    excludeGroups?: boolean
  ) => Promise<{total: number; result: Identity[]}>;
}) {
  const {loading, setLoading, identities, loadNewValues} = useLoadIdentities({
    excludeGroups,
    fetchUsers,
  });

  return (
    <div>
      <button onClick={() => loadNewValues('test')}>Load</button>
      <button onClick={() => setLoading(true)}>Set Loading</button>
      {loading && <div className="loading">Loading...</div>}
      <ul>
        {identities.map((identity) => (
          <li key={identity.id}>{`${identity.name} ${identity.email}`}</li>
        ))}
      </ul>
    </div>
  );
}

it('should initialize with loading true and empty identities', () => {
  const node = shallow(<Mock excludeGroups={false} fetchUsers={mockFetchUsers} />);
  expect(node.find('.loading')).toHaveLength(1);
  expect(node.find('li')).toHaveLength(0);
});

it('should load identities and update state', async () => {
  const node = shallow(<Mock excludeGroups={false} fetchUsers={mockFetchUsers} />);
  node.find('button').at(0).simulate('click');
  await flushPromises();

  expect(node.find('.loading')).toHaveLength(0);
  expect(node.find('li')).toHaveLength(2);
  expect(mockFetchUsers).toHaveBeenCalledWith('test', false);
});

it('should use default searchIdentities function if fetchUsers is not provided', async () => {
  const node = shallow(<Mock excludeGroups={true} />);
  node.find('button').at(0).simulate('click');
  await flushPromises();

  expect(node.find('.loading')).toHaveLength(0);
  expect(node.find('li')).toHaveLength(2);
  expect(searchIdentities).toHaveBeenCalledWith('test', true);
});

it('should update loading state correctly', async () => {
  const node = shallow(<Mock excludeGroups={false} fetchUsers={mockFetchUsers} />);
  expect(node.find('.loading')).toHaveLength(1);

  node.find('button').at(0).simulate('click');
  expect(node.find('.loading')).toHaveLength(1);

  await flushPromises();

  expect(node.find('.loading')).toHaveLength(0);
});
