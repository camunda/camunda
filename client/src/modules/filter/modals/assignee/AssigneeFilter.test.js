/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';
import {AssigneeFilter} from './AssigneeFilter';
import {loadUsers} from './service';
import {Button, LabeledInput} from 'components';

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

it('should add a role', () => {
  const spy = jest.fn();
  const node = shallow(<AssigneeFilter addFilter={spy} {...props} filterType="assignee" />);

  node.find('Typeahead').prop('onChange')('demo');
  node.find('InputGroup').find(Button).simulate('click');

  expect(node.find('.addedValues')).toMatchSnapshot();

  node.find({primary: true}).simulate('click');
  expect(spy).toHaveBeenCalledWith({data: {operator: 'in', values: ['demo']}, type: 'assignee'});
});

it('should remove a role from the list', async () => {
  const node = shallow(
    <AssigneeFilter
      {...props}
      filterType="assignee"
      filterData={{data: {exclude: true, values: ['john']}}}
    />
  );

  runAllEffects();

  node.find(LabeledInput).prop('onChange')();

  expect(node.find('.addedValues').find('[label="john"]')).not.toExist();
});
