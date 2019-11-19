/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {EntityList, Deleter} from 'components';

import {loadProcesses} from './service';
import EventsWithErrorHandling from './Events';

const Events = EventsWithErrorHandling.WrappedComponent;

jest.mock('./service', () => ({
  loadProcesses: jest.fn().mockReturnValue([
    {
      id: 'process1',
      name: 'First Process'
    },
    {
      id: 'process2',
      name: 'Second Process'
    },
    {
      id: 'process3',
      name: 'Third Process'
    }
  ]),
  removeProcess: jest.fn()
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data))
};

it('should load event based processes', () => {
  shallow(<Events {...props} />);

  expect(loadProcesses).toHaveBeenCalled();
});

it('should match snapshot', () => {
  const node = shallow(<Events {...props} />);

  expect(node).toMatchSnapshot();
});

it('should pass a process to the Deleter', () => {
  const node = shallow(<Events {...props} />);

  node
    .find(EntityList)
    .prop('data')[0]
    .actions[1].action();

  expect(node.find(Deleter).prop('entity').id).toBe('process1');
});
