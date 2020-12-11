/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {UserTypeahead} from 'components';

import {AssigneeFilter} from './AssigneeFilter';
import {loadUsers} from './service';

jest.mock('./service', () => ({loadUsers: jest.fn().mockReturnValue(['demo', 'john'])}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  processDefinitionKey: 'key',
  processDefinitionVersions: ['1'],
  tenantIds: ['tenant1'],
};

it('should load existing roles', async () => {
  const node = shallow(<AssigneeFilter {...props} filterType="assignee" />);

  await node.find(UserTypeahead).prop('fetchUsers')('demo');

  expect(loadUsers).toHaveBeenCalledWith('assignee', {
    processDefinitionKey: props.processDefinitionKey,
    tenantIds: props.tenantIds,
    terms: 'demo',
  });
});

it('should add/remove a role', async () => {
  const spy = jest.fn();
  const node = shallow(<AssigneeFilter addFilter={spy} {...props} filterType="assignee" />);

  node.find(UserTypeahead).prop('onChange')([
    {id: 'USER:null', identity: {id: null, name: 'Unassigned'}},
    {id: 'USER:demo', identity: {id: 'demo', name: 'Demo Demo'}},
  ]);

  node.find({primary: true}).simulate('click');
  expect(spy).toHaveBeenCalledWith({
    data: {operator: 'in', values: [null, 'demo']},
    type: 'assignee',
  });

  spy.mockClear();

  node.find(UserTypeahead).prop('onChange')([
    {id: 'USER:null', identity: {id: null, name: 'Unassigned'}},
  ]);

  node.find({primary: true}).simulate('click');
  expect(spy).toHaveBeenCalledWith({
    data: {operator: 'in', values: [null]},
    type: 'assignee',
  });
});
