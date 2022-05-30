/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {EntityList} from 'components';
import {getOptimizeProfile} from 'config';

import {Processes} from './Processes';
import {loadProcesses, updateGoals, updateOwner, loadManagementDashboard} from './service';
import TimeGoalsModal from './TimeGoalsModal';
import EditOwnerModal from './EditOwnerModal';

jest.mock('./service', () => ({
  loadProcesses: jest.fn().mockReturnValue([
    {
      processDefinitionKey: 'defKey',
      processName: 'defName',
      durationGoals: {},
      owner: {id: null},
    },
  ]),
  updateGoals: jest.fn(),
  updateOwner: jest.fn(),
  loadManagementDashboard: jest.fn(),
}));

jest.mock('config', () => ({
  getOptimizeProfile: jest.fn().mockReturnValue('platform'),
}));

beforeEach(() => {
  jest.clearAllMocks();
});

const props = {
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should load processes', async () => {
  const node = shallow(<Processes {...props} />);

  await runAllEffects();

  expect(loadProcesses).toHaveBeenCalled();
  const entityData = node.find(EntityList).prop('data');
  expect(entityData.id);
  expect(node.find(EntityList).prop('data')).toEqual([
    {
      icon: 'data-source',
      id: 'defKey',
      meta: [expect.any(Object), expect.any(Object)],
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

it('should invoke updateGoals when confirming the TimeGoalsModal', async () => {
  const node = shallow(<Processes {...props} />);

  await runAllEffects();
  loadProcesses.mockClear();

  const addGoalBtn = node.find(EntityList).prop('data')[0].meta[1].props.children[1];
  addGoalBtn.props.onClick();
  node.find(TimeGoalsModal).simulate('confirm', [{type: 'targetDuration'}]);

  expect(updateGoals).toHaveBeenCalledWith('defKey', [{type: 'targetDuration'}]);
  expect(loadProcesses).toHaveBeenCalled();
  expect(node.find(TimeGoalsModal)).not.toExist();
});

it('should close the TimeGoalsModal when onClose prop is called', async () => {
  const node = shallow(<Processes {...props} />);

  await runAllEffects();

  const addGoalBtn = node.find(EntityList).prop('data')[0].meta[1].props.children[1];
  addGoalBtn.props.onClick();
  node.find(TimeGoalsModal).simulate('close');

  expect(node.find(TimeGoalsModal)).not.toExist();
});

it('should reload processes when onRemove is called on the timeGoalsModal', async () => {
  const node = shallow(<Processes {...props} />);

  await runAllEffects();

  const addGoalBtn = node.find(EntityList).prop('data')[0].meta[1].props.children[1];
  addGoalBtn.props.onClick();
  node.find(TimeGoalsModal).simulate('remove');

  expect(loadProcesses).toHaveBeenCalled();
});

it('should hide owner column in ccsm mode', async () => {
  getOptimizeProfile.mockReturnValueOnce('ccsm');
  const node = shallow(<Processes {...props} />);

  await runAllEffects();

  expect(node.find(EntityList).prop('columns')[1].key).not.toBe('owner');
  expect(node.find(EntityList).prop('data')[0].meta.length).toBe(1);
});

it('should edit a process owner', async () => {
  const node = shallow(<Processes {...props} />);

  await runAllEffects();

  const addOwnerBtn = node.find(EntityList).prop('data')[0].meta[0].props.children[1];
  addOwnerBtn.props.onClick();
  node.find(EditOwnerModal).simulate('confirm', 'userId');

  expect(updateOwner).toHaveBeenCalledWith('defKey', 'userId');
});

it('should hide owner column in ccsm mode', async () => {
  const testDashboard = {reports: [], availableFilters: []};
  loadManagementDashboard.mockReturnValueOnce(testDashboard);
  const node = shallow(<Processes {...props} />);

  await runAllEffects();

  expect(loadManagementDashboard).toHaveBeenCalled();

  expect(node.find('DashboardView').prop('reports')).toEqual(testDashboard.reports);
  expect(node.find('DashboardView').prop('availableFilters')).toEqual(
    testDashboard.availableFilters
  );
});
