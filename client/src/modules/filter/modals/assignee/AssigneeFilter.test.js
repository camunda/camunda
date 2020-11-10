/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';
import {AssigneeFilter} from './AssigneeFilter';
import {loadUsers} from './service';

jest.mock('./service', () => ({loadUsers: jest.fn().mockReturnValue(['demo', 'john'])}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  processDefinitionKey: 'key',
  processDefinitionVersions: ['1'],
  tenantIds: ['tenant1'],
};

it('should load existing roles', () => {
  shallow(<AssigneeFilter {...props} filterType="assignee" />);

  runAllEffects();

  expect(loadUsers).toHaveBeenCalledWith('assignee', {
    processDefinitionKey: props.processDefinitionKey,
    processDefinitionVersions: props.processDefinitionVersions,
    tenantIds: props.tenantIds,
  });
});

it('should add/remove a role', () => {
  const spy = jest.fn();
  const node = shallow(<AssigneeFilter addFilter={spy} {...props} filterType="assignee" />);

  runAllEffects();

  node.find('MultiSelect').prop('onAdd')(null);
  node.find('MultiSelect').prop('onAdd')('demo');

  expect(node.find({value: 'demo'})).not.toExist();
  node.find({primary: true}).simulate('click');
  expect(spy).toHaveBeenCalledWith({
    data: {operator: 'in', values: [null, 'demo']},
    type: 'assignee',
  });

  spy.mockClear();

  node.find('MultiSelect').prop('onRemove')('demo');

  expect(node.find({value: 'demo'})).toExist();
  node.find({primary: true}).simulate('click');
  expect(spy).toHaveBeenCalledWith({
    data: {operator: 'in', values: [null]},
    type: 'assignee',
  });
});
