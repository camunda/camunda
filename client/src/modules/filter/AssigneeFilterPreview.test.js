/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {loadUserNames} from './service';

import {AssigneeFilterPreview} from './AssigneeFilterPreview';

jest.mock('./service', () => ({
  loadUserNames: jest.fn().mockReturnValue([
    {
      id: 'demo',
      name: 'Demo Demo',
    },
  ]),
}));

const props = {
  mightFail: (data, cb) => cb(data),
  filter: {
    type: 'assignee',
    filterLevel: 'instance',
    data: {
      operator: 'in',
      values: [null, 'demo'],
    },
  },
};

beforeEach(() => {
  loadUserNames.mockClear();
});

it('should display the id if no name is resolved', () => {
  const node = shallow(<AssigneeFilterPreview {...props} />);

  expect(node.find('b').at(1).prop('children')).toBe('demo');
});

it('should load assignee names based on provided ids', () => {
  shallow(<AssigneeFilterPreview {...props} />);

  runLastEffect();

  expect(loadUserNames).toHaveBeenCalledWith('assignee', ['demo']);
});

it('should display assignee names', () => {
  const node = shallow(<AssigneeFilterPreview {...props} />);

  runLastEffect();

  expect(node.find('b').at(1).prop('children')).toBe('Demo Demo');
});

it('should accept a custom function to get user names', () => {
  const getNames = jest.fn().mockReturnValue([
    {
      id: 'demo',
      name: 'Harald',
    },
  ]);
  const node = shallow(<AssigneeFilterPreview {...props} getNames={getNames} />);

  runLastEffect();

  expect(loadUserNames).not.toHaveBeenCalled();
  expect(getNames).toHaveBeenCalledWith('assignee', ['demo']);
  expect(node.find('b').at(1).prop('children')).toBe('Harald');
});
