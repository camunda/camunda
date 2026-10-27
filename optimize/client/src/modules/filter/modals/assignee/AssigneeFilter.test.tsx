/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComponentProps} from 'react';
import {runLastEffect} from '__mocks__/react';
import {shallow} from 'enzyme';

import {User, UserTypeahead} from 'components';

import AssigneeFilter from './AssigneeFilter';
import {loadUsersByDefinition, loadUsersByReportIds, getUsersById} from './service';

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn().mockImplementation(() => ({
    mightFail: jest.fn().mockImplementation(async (data, cb, err) => {
      try {
        const awaitedData = await data;
        return cb(awaitedData);
      } catch (e) {
        err?.(e);
      }
    }),
  })),
}));

jest.mock('./service', () => ({
  loadUsersByDefinition: jest.fn().mockReturnValue(['demo', 'john']),
  loadUsersByReportIds: jest.fn().mockReturnValue(['demo', 'john']),
  getUsersById: jest.fn().mockReturnValue([
    {type: 'user', id: 'demo', name: 'Demo Demo'},
    {type: 'user', id: 'john', name: 'Johnny'},
  ]),
}));

const props: ComponentProps<typeof AssigneeFilter> = {
  definitions: [{identifier: 'definition', key: 'key', versions: ['1'], tenantIds: ['tenant1']}],
  filterType: 'assignee',
  filterLevel: 'view',
  close: jest.fn(),
  addFilter: jest.fn(),
};

const filterData: ComponentProps<typeof AssigneeFilter>['filterData'] = {
  type: 'assignee',
  data: {
    operator: 'not in',
    values: ['demo', 'john'],
  },
  appliedTo: ['definition'],
};

beforeEach(() => {
  (loadUsersByDefinition as jest.Mock).mockClear();
  (loadUsersByReportIds as jest.Mock).mockClear();
  (getUsersById as jest.Mock).mockClear();
});

it('should load existing roles', async () => {
  const node = shallow(<AssigneeFilter {...props} />);

  await node.find(UserTypeahead).prop('fetchUsers')?.('demo');

  expect(loadUsersByDefinition).toHaveBeenCalledWith('assignee', {
    processDefinitionKey: props.definitions[0]?.key,
    tenantIds: props.definitions[0]?.tenantIds,
    terms: 'demo',
  });
});

it('should load existing roles by provided report ids', async () => {
  const node = shallow(<AssigneeFilter {...props} reportIds={['1', '2']} />);

  await node.find(UserTypeahead).prop('fetchUsers')?.('demo');

  expect(loadUsersByReportIds).toHaveBeenCalledWith('assignee', {
    reportIds: ['1', '2'],
    terms: 'demo',
  });
});

it('should add/remove a role', async () => {
  const spy = jest.fn();
  const node = shallow(<AssigneeFilter {...props} addFilter={spy} />);

  node.find(UserTypeahead).prop('onChange')([
    {id: 'USER:null', identity: {id: null, name: 'Unassigned'}},
    {id: 'USER:demo', identity: {id: 'demo', name: 'Demo Demo'}},
  ]);

  node.find('.confirm').simulate('click');
  expect(spy).toHaveBeenCalledWith({
    data: {operator: 'in', values: [null, 'demo']},
    type: 'assignee',
    appliedTo: ['definition'],
  });

  spy.mockClear();

  node.find(UserTypeahead).prop('onChange')([
    {id: 'USER:null', identity: {id: null, name: 'Unassigned'}} as User,
  ]);

  node.find('.confirm').simulate('click');
  expect(spy).toHaveBeenCalledWith({
    data: {operator: 'in', values: [null]},
    type: 'assignee',
    appliedTo: ['definition'],
  });
});

it('should allow rendering a pretext if provided', async () => {
  const spy = jest.fn().mockReturnValue(<span className="pretext">pretext value</span>);
  const node = shallow(<AssigneeFilter {...props} getPretext={spy} filterData={filterData} />);

  await runLastEffect();

  expect(spy).toHaveBeenCalledWith();
  expect(node.find('.pretext')).toExist();
});

it('should allow rendering a posttext if provided', async () => {
  const spy = jest.fn().mockReturnValue(<span className="posttext">posttext value</span>);
  const node = shallow(<AssigneeFilter {...props} getPosttext={spy} filterData={filterData} />);

  await runLastEffect();

  expect(spy).toHaveBeenCalledWith();
  expect(node.find('.posttext')).toExist();
});

it('should allow forcing the add button to be enabled', () => {
  const node = shallow(<AssigneeFilter {...props} />);

  expect(node.find('.confirm').prop('disabled')).toBe(true);

  node.setProps({forceEnabled: () => true});

  expect(node.find('.confirm').prop('disabled')).toBe(false);
});
