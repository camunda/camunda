/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {addNotification} from 'notifications';
import {EntityList} from 'components';
import {getOptimizeProfile} from 'config';

import {Processes} from './Processes';
import {loadProcesses, updateProcess, loadManagementDashboard} from './service';
import ConfigureProcessModal from './ConfigureProcessModal';

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useHistory: jest.fn().mockReturnValue({push: jest.fn()}),
}));

jest.mock('notifications', () => ({addNotification: jest.fn()}));

jest.mock('./service', () => ({
  loadProcesses: jest.fn().mockReturnValue([
    {
      processDefinitionKey: 'defKey',
      processDefinitionName: 'defName',
      kpis: [],
      owner: {id: null},
    },
  ]),
  updateProcess: jest.fn(),
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
  user: {name: 'John Doe', authorizations: ['entity_editor']},
};

it('should load processes', async () => {
  const node = shallow(<Processes {...props} />);

  await runAllEffects();

  expect(loadProcesses).toHaveBeenCalled();
  const entityData = node.find(EntityList).prop('data');
  expect(entityData.id);
  expect(node.find(EntityList).prop('data')).toEqual([
    {
      icon: 'dashboard-optimize',
      id: 'defKey',
      meta: expect.any(Array),
      name: 'defName',
      type: 'Process',
      link: 'dashboard/instant/defKey/',
      actions: [
        {
          action: expect.any(Function),
          text: 'Configure',
        },
      ],
    },
  ]);
});

it('should load processes with sort parameters', () => {
  const node = shallow(<Processes {...props} />);

  node.find('EntityList').prop('onChange')('lastModifier', 'desc');

  expect(loadProcesses).toHaveBeenCalledWith('lastModifier', 'desc');
  expect(node.find('EntityList').prop('sorting')).toEqual({key: 'lastModifier', order: 'desc'});
});

it('should hide owner column and process config button in ccsm mode', async () => {
  getOptimizeProfile.mockReturnValueOnce('ccsm');
  const node = shallow(<Processes {...props} />);

  await runAllEffects();

  const columns = node.find(EntityList).prop('columns');
  const data = node.find(EntityList).prop('data');

  expect(columns[1]).not.toBe('owner');
  expect(columns.length).toBe(3);
  expect(data[0].meta.length).toBe(2);
  expect(data[0].actions.length).toBe(0);
});

it('should edit a process config', async () => {
  const testConfig = {
    ownerId: 'testId',
    processDigest: {enabled: true, checkInterval: {value: 1, unit: 'months'}},
  };

  const node = shallow(<Processes {...props} />);
  await runAllEffects();

  const configureProcessBtn = node.find(EntityList).prop('data')[0].actions[0];
  configureProcessBtn.action();

  node.find(ConfigureProcessModal).simulate('confirm', testConfig);
  expect(updateProcess).toHaveBeenCalledWith('defKey', testConfig);
});

it('should show process update notification if digest & email are enabled', async () => {
  const testConfig = {
    ownerId: 'testId',
    processDigest: {enabled: true, checkInterval: {value: 1, unit: 'months'}},
  };

  const node = shallow(<Processes {...props} />);
  await runAllEffects();

  const configureProcessBtn = node.find(EntityList).prop('data')[0].actions[0];
  configureProcessBtn.action();

  node.find(ConfigureProcessModal).simulate('confirm', testConfig, true, 'testName');
  expect(addNotification).toHaveBeenCalled();
});

it('should pass loaded reports and filters to the management dashboard view component', async () => {
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

it('should filter out invalid kpis', async () => {
  const validKpi = {
    reportName: 'report Name',
    type: 'quality',
    value: '123',
    target: '300',
    isBelow: true,
    measure: 'frequency',
  };

  loadProcesses.mockReturnValueOnce([
    {
      kpis: [
        {
          ...validKpi,
          value: null,
        },
        {
          ...validKpi,
          target: null,
        },
        validKpi,
      ],
    },
  ]);
  const node = shallow(<Processes {...props} />);

  await runAllEffects();

  const entityData = node.find(EntityList).prop('data');
  expect(entityData[0].meta[2].props.content.props.kpis).toEqual([validKpi]);
});

it('should hide the link to view the dashboard if the user has no edit rights', async () => {
  const node = shallow(<Processes {...props} user={{...props.user, authorizations: []}} />);

  await runAllEffects();

  expect(node.find(EntityList).prop('data')[0].onClick).not.toBeDefined();
});

it('display the search info correctly', async () => {
  const node = shallow(<Processes {...props} />);

  await runAllEffects();

  const text = node.find(EntityList).prop('displaySearchInfo')('', 1).props.children;
  expect(text).toBe('1 process listed');

  const textWithQuery = node.find(EntityList).prop('displaySearchInfo')('def', 1).props.children;
  expect(textWithQuery).toBe('1 of 1 process listed');
});
