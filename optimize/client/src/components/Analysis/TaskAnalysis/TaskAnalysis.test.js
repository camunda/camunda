/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from 'react';
import {shallow} from 'enzyme';

import {loadProcessDefinitionXml, getFlowNodeNames, incompatibleFilters} from 'services';
import {track} from 'tracking';

import {loadNodesOutliers, loadDurationData, loadCommonOutliersVariables} from './service';
import TaskAnalysis from './TaskAnalysis';
import OutlierDetailsModal from './OutlierDetailsModal';
import OutlierDetailsTable from './OutlierDetailsTable';

jest.mock('./service', () => {
  return {
    ...jest.requireActual('./service'),
    loadNodesOutliers: jest.fn().mockReturnValue({
      nodeKey: {
        heat: 50,
        higherOutlierHeat: 20,
        higherOutlier: {count: 20, relation: 1.3},
        totalCount: 123,
      },
    }),
    loadDurationData: jest.fn().mockReturnValue([{key: 'test', value: 'testVal', outlier: false}]),
    loadCommonOutliersVariables: jest.fn().mockReturnValue({}),
  };
});

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadProcessDefinitionXml: jest.fn(),
    getFlowNodeNames: jest.fn().mockReturnValue({nodeKey: 'nodeName'}),
    incompatibleFilters: jest.fn(),
  };
});

jest.mock('tracking', () => ({track: jest.fn()}));

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn().mockImplementation(() => ({
    mightFail: jest.fn().mockImplementation((data, cb, err, final) => {
      cb(data);
      final?.();
    }),
  })),
  useUser: jest.fn().mockImplementation(() => ({
    user: null,
  })),
}));

function updateConfig(node, newConfig) {
  node.find('OutlierControlPanel').prop('onChange')(newConfig);
}

it('should contain a control panel', () => {
  const node = shallow(<TaskAnalysis />);

  expect(node.find('OutlierControlPanel')).toExist();
});

it('should load the process definition xml when the process definition id is updated', () => {
  const node = shallow(<TaskAnalysis />);

  loadProcessDefinitionXml.mockClear();
  updateConfig(node, {
    processDefinitionKey: 'someKey',
    processDefinitionVersions: ['someVersion'],
    tenantIds: ['a', 'b'],
  });

  runAllEffects();

  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('someKey', 'someVersion', 'a');
  expect(track).toHaveBeenCalledWith('startOutlierAnalysis', {processDefinitionKey: 'someKey'});
});

it('should load outlier data and flownode names when the process definition version changes', async () => {
  loadNodesOutliers.mockClear();
  getFlowNodeNames.mockClear();
  const node = shallow(<TaskAnalysis />);

  const prevConfig = {
    processDefinitionKey: 'someKey',
    processDefinitionVersions: ['someVersion'],
    tenantIds: [],
    filters: [],
  };

  runAllEffects();

  updateConfig(node, prevConfig);
  updateConfig(node, {...prevConfig, processDefinitionVersions: ['anotherVersion']});

  await flushPromises();
  runAllEffects();

  expect(getFlowNodeNames).toHaveBeenCalled();
  expect(loadNodesOutliers).toHaveBeenCalled();
});

it('should load outlier data and flownode names when the tenants change', async () => {
  loadNodesOutliers.mockClear();
  getFlowNodeNames.mockClear();
  const node = shallow(<TaskAnalysis />);
  const prevConfig = {
    processDefinitionKey: 'someKey',
    processDefinitionVersions: ['someVersion'],
    tenantIds: [],
    filters: [],
  };
  updateConfig(node, prevConfig);
  updateConfig(node, {
    ...prevConfig,
    tenantIds: ['a', 'b'],
  });

  await flushPromises();
  runAllEffects();

  expect(getFlowNodeNames).toHaveBeenCalled();
  expect(loadNodesOutliers).toHaveBeenCalled();
});

it('should load outlier data and flownode names when filters change', async () => {
  loadNodesOutliers.mockClear();
  getFlowNodeNames.mockClear();
  const node = shallow(<TaskAnalysis />);
  const prevConfig = {
    processDefinitionKey: 'someKey',
    processDefinitionVersions: ['someVersion'],
    tenantIds: [],
    filters: [],
  };
  updateConfig(node, prevConfig);
  updateConfig(node, {
    ...prevConfig,
    filters: [
      {
        type: 'completedInstancesOnly',
        appliedTo: ['all'],
        filterLevel: 'instance',
      },
    ],
  });

  await flushPromises();
  runAllEffects();

  expect(getFlowNodeNames).toHaveBeenCalled();
  expect(loadNodesOutliers).toHaveBeenCalled();
});

it('should not try to load outlier data if no process definition is selected', async () => {
  loadNodesOutliers.mockClear();
  getFlowNodeNames.mockClear();

  const node = shallow(<TaskAnalysis />);

  updateConfig(node, {});

  runAllEffects();
  await flushPromises();

  expect(loadNodesOutliers).not.toHaveBeenCalled();
});

it('should create correct flownodes higher outlier heat object', async () => {
  loadNodesOutliers.mockClear();
  getFlowNodeNames.mockClear();
  loadProcessDefinitionXml.mockReturnValue('xml');

  const node = shallow(<TaskAnalysis />);

  updateConfig(node, {
    processDefinitionKey: 'someKey',
    processDefinitionVersions: ['someVersion'],
    tenantIds: ['a', 'b'],
  });

  await flushPromises();
  runAllEffects();
  await flushPromises();

  expect(node.find('HeatmapOverlay').prop('data')).toEqual({nodeKey: 20});
});

it('display load chart data and display details modal when loadChartData is called', async () => {
  loadNodesOutliers.mockClear();
  getFlowNodeNames.mockClear();

  const node = shallow(<TaskAnalysis />);

  updateConfig(node, {
    processDefinitionKey: 'someKey',
    processDefinitionVersions: ['someVersion'],
    tenantIds: ['a', 'b'],
  });

  await flushPromises();
  runAllEffects();
  await flushPromises();

  const nodeData = {higherOutlier: {boundValue: 20}};

  await node.find('OutlierDetailsTable').prop('onDetailsClick')('nodeKey', nodeData);

  expect(loadDurationData).toHaveBeenCalled();

  expect(node.find(OutlierDetailsModal).prop('selectedOutlierNode')).toEqual({
    name: 'nodeName',
    id: 'nodeKey',
    ...nodeData,
    data: [{key: 'test', outlier: false, value: 'testVal'}],
  });
});

it('should open details modal on node click', async () => {
  loadNodesOutliers.mockClear();
  getFlowNodeNames.mockClear();
  loadProcessDefinitionXml.mockReturnValue('xml');
  const node = shallow(<TaskAnalysis />);
  updateConfig(node, {
    processDefinitionKey: 'someKey',
    processDefinitionVersions: ['someVersion'],
    tenantIds: ['a', 'b'],
  });

  await flushPromises();
  runAllEffects();
  await flushPromises();

  node.find('HeatmapOverlay').prop('onNodeClick')({element: {id: 'nodeKey'}});

  expect(node.find(OutlierDetailsModal)).toExist();
});

it('should load variables data and display details table', async () => {
  getFlowNodeNames.mockClear();
  loadNodesOutliers.mockClear();

  const node = shallow(<TaskAnalysis />);

  updateConfig(node, {
    processDefinitionKey: 'someKey',
    processDefinitionVersions: ['someVersion'],
    tenantIds: ['a', 'b'],
  });

  await flushPromises();
  runAllEffects();

  expect(getFlowNodeNames).toHaveBeenCalled();
  expect(loadNodesOutliers).toHaveBeenCalled();
  expect(node.find(OutlierDetailsTable)).toExist();
});

it('should display an empty state if no outliers found', async () => {
  loadProcessDefinitionXml.mockReturnValue('xml');
  loadNodesOutliers.mockReturnValue({});

  const node = shallow(<TaskAnalysis />);

  updateConfig(node, {
    processDefinitionKey: 'someKey',
    processDefinitionVersions: ['someVersion'],
    tenantIds: ['a', 'b'],
  });

  await flushPromises();
  await runAllEffects();

  expect(node.find('.noOutliers')).toExist();
});

it('should show a warning message when there are incompatible filters', async () => {
  incompatibleFilters.mockReturnValue(true);
  const node = await shallow(<TaskAnalysis />);

  runAllEffects();
  await flushPromises();

  expect(node.find('InlineNotification')).toExist();
});
