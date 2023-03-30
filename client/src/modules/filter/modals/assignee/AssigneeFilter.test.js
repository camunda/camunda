/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {UserTypeahead, CarbonModal as Modal} from 'components';

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
  definitions: [{identifier: 'definition', key: 'key', versions: ['1'], tenantIds: ['tenant1']}],
  filterType: 'assignee',
};

const filterData = {
  type: 'assignee',
  data: {
    operator: 'not in',
    values: ['demo', 'john'],
  },
  appliedTo: ['definition'],
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
    processDefinitionKey: props.definitions[0].key,
    tenantIds: props.definitions[0].tenantIds,
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

  node.find(Modal.Footer).prop('onRequestSubmit')();
  expect(spy).toHaveBeenCalledWith({
    data: {operator: 'in', values: [null, 'demo']},
    type: 'assignee',
    appliedTo: ['definition'],
  });

  spy.mockClear();

  node.find(UserTypeahead).prop('onChange')([
    {id: 'USER:null', identity: {id: null, name: 'Unassigned'}},
  ]);

  node.find(Modal.Footer).prop('onRequestSubmit')();
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

  expect(node.find(Modal.Footer).prop('primaryButtonDisabled')).toBe(true);

  node.setProps({forceEnabled: () => true});

  expect(node.find(Modal.Footer).prop('primaryButtonDisabled')).toBe(false);
});
