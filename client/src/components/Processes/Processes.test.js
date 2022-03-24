/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {runLastEffect} from 'react';
import {shallow} from 'enzyme';

import {EntityList} from 'components';

import {Processes} from './Processes';
import {loadProcesses, updateGoals} from './service';
import TimeGoalsModal from './TimeGoalsModal';

jest.mock('./service', () => ({
  loadProcesses: jest.fn(),
  updateGoals: jest.fn(),
}));

beforeEach(() => {
  jest.clearAllMocks();
});

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should load processes', () => {
  loadProcesses.mockReturnValue([
    {processDefinitionKey: 'defKey', processName: 'defName', durationGoals: {}, owner: 'test'},
  ]);
  const node = shallow(<Processes {...props} />);

  runLastEffect();

  expect(loadProcesses).toHaveBeenCalled();
  const entityData = node.find(EntityList).prop('data');
  expect(entityData.id);
  expect(node.find(EntityList).prop('data')).toEqual([
    {
      icon: 'data-source',
      id: 'defKey',
      meta: ['test', expect.any(Object)],
      name: 'defName',
      type: 'Process',
    },
  ]);
});

it('should load processes with sort parameters', () => {
  const node = shallow(<Processes {...props} />);

  node.find('EntityList').prop('onChange')('lastModifier', 'desc');

  expect(loadProcesses).toHaveBeenCalledWith('lastModifier', 'desc');
  expect(node.find('EntityList').prop('sorting')).toEqual({key: 'lastModifier', order: 'desc'});
});

it('should invoke updateGoals when confirming the TimeGoalsModal', () => {
  const node = shallow(<Processes {...props} />);

  runLastEffect();
  loadProcesses.mockClear();

  const addGoalBtn = node.find(EntityList).prop('data')[0].meta[1].props.children[1];
  addGoalBtn.props.onClick();
  node.find(TimeGoalsModal).simulate('confirm', [{type: 'targetDuration'}]);

  expect(updateGoals).toHaveBeenCalledWith('defKey', [{type: 'targetDuration'}]);
  expect(loadProcesses).toHaveBeenCalled();
  expect(node.find(TimeGoalsModal)).not.toExist();
});

it('should close the TimeGoalsModal when onClose prop is called', () => {
  const node = shallow(<Processes {...props} />);

  runLastEffect();

  const addGoalBtn = node.find(EntityList).prop('data')[0].meta[1].props.children[1];
  addGoalBtn.props.onClick();
  node.find(TimeGoalsModal).simulate('close');

  expect(node.find(TimeGoalsModal)).not.toExist();
});

it('should reload processes when onRemove is called on the timeGoalsModal', async () => {
  const node = shallow(<Processes {...props} />);

  runLastEffect();

  const addGoalBtn = node.find(EntityList).prop('data')[0].meta[1].props.children[1];
  addGoalBtn.props.onClick();
  node.find(TimeGoalsModal).simulate('remove');

  expect(loadProcesses).toHaveBeenCalled();
});
