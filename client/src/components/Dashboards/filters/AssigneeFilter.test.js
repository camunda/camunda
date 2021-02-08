/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {UserTypeahead} from 'components';

import {AssigneeFilter} from './AssigneeFilter';

const props = {
  filter: null,
  type: 'assignee',
  config: {
    operator: 'not in',
    values: ['user1', 'user2', null],
    allowCustomValues: false,
  },
  setFilter: jest.fn(),
  reports: [{id: 'reportA'}],
  mightFail: (data, cb) => cb(data),
};

jest.mock('./service', () => ({
  getAssigneeNames: jest.fn().mockReturnValue([
    {id: 'user1', name: 'User 1'},
    {id: 'user2', name: 'User 2'},
  ]),
}));

beforeEach(() => {
  props.setFilter.mockClear();
});

it('should show the operator when no value is selected', () => {
  const node = shallow(<AssigneeFilter {...props} />);

  expect(node.find('Popover').prop('title')).toMatchSnapshot();
});

it('should allow selecting values', () => {
  const node = shallow(<AssigneeFilter {...props} />);

  runLastEffect();

  const valueSwitch = node.find('Switch').first();

  expect(valueSwitch).toExist();
  expect(valueSwitch.prop('label')).toBe('User 1');

  valueSwitch.simulate('change', {target: {checked: true}});

  expect(props.setFilter).toHaveBeenCalledWith({operator: 'not in', values: ['user1']});
});

it('should abbreviate multiple string selections', () => {
  const node = shallow(
    <AssigneeFilter {...props} filter={{operator: 'not in', values: ['user1', null]}} />
  );

  expect(node.find('Popover').prop('title')).toMatchSnapshot();
});

it('should show an input field for custom values', () => {
  const node = shallow(
    <AssigneeFilter
      {...props}
      filter={{operator: 'not in', values: ['user1']}}
      config={{
        operator: 'not in',
        values: ['user1', 'user2', null],
        allowCustomValues: true,
      }}
    />
  );

  runLastEffect();

  expect(node.find(UserTypeahead)).toExist();

  node
    .find(UserTypeahead)
    .simulate('change', [
      {identity: {id: 'john', name: 'jonny'}},
      {identity: {id: 'userX', name: 'X Man'}},
    ]);

  expect(props.setFilter).toHaveBeenCalledWith({
    operator: 'not in',
    values: ['user1', 'john', 'userX'],
  });

  node
    .find('Switch')
    .last()
    .simulate('change', {target: {checked: false}});

  expect(props.setFilter).toHaveBeenCalledWith({
    operator: 'not in',
    values: ['user1'],
  });
});
