/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from 'react';
import {shallow} from 'enzyme';
import {DecisionTree, Settings} from '@carbon/icons-react';

import {addNotification} from 'notifications';
import {EntityList} from 'components';
import {isUserSearchAvailable, getOptimizeDatabase} from 'config';
import {track} from 'tracking';

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
  isUserSearchAvailable: jest.fn().mockReturnValue(true),
  getOptimizeDatabase: jest.fn().mockReturnValue('elasticsearch'),
}));

jest.mock('tracking', () => ({track: jest.fn()}));

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
  const entityData = node.find(EntityList).prop('rows');
  expect(entityData.id);
  expect(node.find(EntityList).prop('rows')).toEqual([
    {
      icon: <DecisionTree />,
      id: 'defKey',
      meta: expect.any(Array),
      name: 'defName',
      type: 'Process',
      link: 'dashboard/instant/defKey/',
      actions: [
        {
          icon: <Settings />,
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

it('should hide owner column and process config button if user search is not available', async () => {
  isUserSearchAvailable.mockReturnValueOnce(false);
  const node = shallow(<Processes {...props} />);

  await runAllEffects();

  const headers = node.find(EntityList).prop('headers');
  const rows = node.find(EntityList).prop('rows');

  expect(headers[1]).not.toBe('owner');
  expect(headers.length).toBe(3);
  expect(rows[0].meta.length).toBe(2);
  expect(rows[0].actions.length).toBe(0);
});

it('should edit a process config', async () => {
  const testConfig = {
    ownerId: 'testId',
    processDigest: {enabled: true, checkInterval: {value: 1, unit: 'months'}},
  };

  const node = shallow(<Processes {...props} />);
  await runAllEffects();

  const configureProcessBtn = node.find(EntityList).prop('rows')[0].actions[0];
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

  const configureProcessBtn = node.find(EntityList).prop('rows')[0].actions[0];
  configureProcessBtn.action();

  node.find(ConfigureProcessModal).simulate('confirm', testConfig, true, 'testName');
  expect(addNotification).toHaveBeenCalled();
  expect(track).toHaveBeenCalledWith('emailDigestEnabled', {processDefinitionKey: 'defKey'});
});

it('should pass loaded tiles to the management dashboard view component', async () => {
  const testDashboard = {tiles: []};
  loadManagementDashboard.mockReturnValueOnce(testDashboard);
  const node = shallow(<Processes {...props} />);

  await runAllEffects();
  await flushPromises();

  expect(loadManagementDashboard).toHaveBeenCalled();

  expect(node.find('DashboardView').prop('tiles')).toEqual(testDashboard.tiles);
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

  const rows = node.find(EntityList).prop('rows');
  expect(rows[0].meta[2].props.kpis).toEqual([validKpi]);
});

it('should hide the link to view the dashboard if the user has no edit rights', async () => {
  const node = shallow(<Processes {...props} user={{...props.user, authorizations: []}} />);

  await runAllEffects();

  expect(node.find(EntityList).prop('rows')[0].onClick).not.toBeDefined();
});

it('display the search info correctly', async () => {
  const node = shallow(<Processes {...props} />);

  await runAllEffects();

  const text = node.find(EntityList).prop('description')('', 1);
  expect(text).toBe('1 process listed.');

  const textWithQuery = node.find(EntityList).prop('description')('def', 1);
  expect(textWithQuery).toBe('1 of 1 process listed.');
});

it('should not display adoption dashboard when running optimize with opensearch', async () => {
  const node = shallow(<Processes {...props} />);

  await runAllEffects();
  await flushPromises();

  expect(node.find('.processOverview')).toExist();

  getOptimizeDatabase.mockReturnValue('opensearch');
  await runAllEffects();
  await flushPromises();

  expect(node.find('.processOverview')).not.toExist();
});

it('should not display the link to view the dashboard in opensearch mode', async () => {
  getOptimizeDatabase.mockReturnValue('opensearch');
  const node = shallow(<Processes {...props} />);

  await runAllEffects();
  await flushPromises();

  expect(node.find(EntityList).prop('rows')[0].link).not.toBeDefined();
});
