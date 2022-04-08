/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {EntityList, Deleter} from 'components';

import PublishModal from './PublishModal';
import {loadProcesses} from './service';
import {EventsProcesses} from './EventsProcesses';
import GenerationModal from './GenerationModal';

jest.mock('./service', () => ({
  loadProcesses: jest.fn().mockReturnValue([
    {
      id: 'process1',
      name: 'First Process',
      lastModified: '2019-11-18T12:29:37+0000',
      state: 'mapped',
    },
    {
      id: 'process2',
      name: 'Second Process',
      lastModified: '2019-11-18T12:29:37+0000',
      state: 'published',
    },
    {
      id: 'process3',
      name: 'Third Process',
      lastModified: '2019-11-18T12:29:37+0000',
      state: 'unpublished_changes',
    },
  ]),
  removeProcess: jest.fn(),
}));

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should load event based processes', () => {
  shallow(<EventsProcesses {...props} />);

  expect(loadProcesses).toHaveBeenCalled();
});

it('should pass a process to the Deleter', () => {
  const node = shallow(<EventsProcesses {...props} />);

  node.find(EntityList).prop('data')[0].actions[3].action();

  expect(node.find(Deleter).prop('entity').id).toBe('process1');
});

it('should pass a process id to the PublishModal', () => {
  const node = shallow(<EventsProcesses {...props} />);

  node.find(EntityList).prop('data')[0].actions[0].action();

  expect(node.find(PublishModal).prop('id')).toBe('process1');
  expect(node.find(PublishModal).prop('republish')).toBe(false);
});

it('should correctly set the republish prop on the PublishModal', () => {
  const node = shallow(<EventsProcesses {...props} />);

  node.find(EntityList).prop('data')[2].actions[0].action();

  expect(node.find(PublishModal).prop('republish')).toBe(true);
});

it('should open generation modal', () => {
  const node = shallow(<EventsProcesses {...props} />);
  node.instance().toggleGenerationModal();
  expect(node.find(GenerationModal)).toExist();
});
