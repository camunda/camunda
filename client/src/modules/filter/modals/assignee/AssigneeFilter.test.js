/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {UserTypeahead, Button} from 'components';

import {AssigneeFilter} from './AssigneeFilter';
import {loadUsersByDefinition, loadUsersByReportIds, getUsersById} from './service';

jest.mock('./service', () => ({
  loadUsersByDefinition: jest.fn().mockReturnValue(['demo', 'john']),
  loadUsersByReportIds: jest.fn().mockReturnValue(['demo', 'john']),
  getUsersById: jest.fn().mockReturnValue([
    {type: 'user', id: 'demo', name: 'Demo Demo'},
    {type: 'user', id: 'john', name: 'Johnny'},
  ]),
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  processDefinitionKey: 'key',
  processDefinitionVersions: ['1'],
  tenantIds: ['tenant1'],
  filterType: 'assignee',
};

const filterData = {
  type: 'assignee',
  data: {
    operator: 'not in',
    values: ['demo', 'john'],
  },
};

beforeEach(() => {
  loadUsersByDefinition.mockClear();
  loadUsersByReportIds.mockClear();
  getUsersById.mockClear();
});

it('should load existing roles', async () => {
  const node = shallow(<AssigneeFilter {...props} />);

  await node.find(UserTypeahead).prop('fetchUsers')('demo');

  expect(loadUsersByDefinition).toHaveBeenCalledWith('assignee', {
    processDefinitionKey: props.processDefinitionKey,
    tenantIds: props.tenantIds,
    terms: 'demo',
  });
});

it('should load existing roles by provided report ids', async () => {
  const node = shallow(
    <AssigneeFilter mightFail={props.mightFail} filterType="assignee" reportIds={['1', '2']} />
  );

  await node.find(UserTypeahead).prop('fetchUsers')('demo');

  expect(loadUsersByReportIds).toHaveBeenCalledWith('assignee', {
    reportIds: ['1', '2'],
    terms: 'demo',
  });
});

it('should add/remove a role', async () => {
  const spy = jest.fn();
  const node = shallow(<AssigneeFilter addFilter={spy} {...props} />);

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

it('should allow rendering a pretext if provided', async () => {
  const spy = jest.fn().mockReturnValue(<span className="pretext">pretext value</span>);
  const node = shallow(<AssigneeFilter {...props} getPretext={spy} filterData={filterData} />);

  await runLastEffect();

  expect(spy).toHaveBeenCalledWith(
    [
      {id: 'USER:demo', identity: {id: 'demo', name: 'Demo Demo', type: 'user'}},
      {id: 'USER:john', identity: {id: 'john', name: 'Johnny', type: 'user'}},
    ],
    'not in'
  );
  expect(node.find('.pretext')).toExist();
});

it('should allow rendering a posttext if provided', async () => {
  const spy = jest.fn().mockReturnValue(<span className="posttext">posttext value</span>);
  const node = shallow(<AssigneeFilter {...props} getPosttext={spy} filterData={filterData} />);

  await runLastEffect();

  expect(spy).toHaveBeenCalledWith(
    [
      {id: 'USER:demo', identity: {id: 'demo', name: 'Demo Demo', type: 'user'}},
      {id: 'USER:john', identity: {id: 'john', name: 'Johnny', type: 'user'}},
    ],
    'not in'
  );
  expect(node.find('.posttext')).toExist();
});

it('should allow forcing the add button to be enabled', () => {
  const node = shallow(<AssigneeFilter {...props} />);

  expect(node.find(Button).last()).toBeDisabled();

  node.setProps({forceEnabled: () => true});

  expect(node.find(Button).last()).not.toBeDisabled();
});
