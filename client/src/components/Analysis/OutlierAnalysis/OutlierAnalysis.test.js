/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {loadNodesOutliers, loadDurationData} from './service';

import OutlierAnalysis from './OutlierAnalysis';

import {loadProcessDefinitionXml, getFlowNodeNames} from 'services';

jest.mock('./service', () => {
  return {
    loadNodesOutliers: jest.fn().mockReturnValue({
      nodeKey: {heat: 50, higherOutlierHeat: 20, higherOutlier: {count: 20, relation: 1.3}}
    }),
    loadDurationData: jest.fn().mockReturnValue([{key: 'test', value: 'testVal', outlier: false}])
  };
});

jest.mock('services', () => {
  return {
    ...jest.requireActual('services'),
    loadProcessDefinitionXml: jest.fn(),
    getFlowNodeNames: jest.fn().mockReturnValue({nodeKey: 'nodeName'})
  };
});

it('should contain a control panel', () => {
  const node = shallow(<OutlierAnalysis />);

  expect(node.find('OutlierControlPanel')).toExist();
});

it('should load the process definition xml when the process definition id is updated', () => {
  const node = shallow(<OutlierAnalysis />);

  loadProcessDefinitionXml.mockClear();
  node.instance().updateConfig({
    processDefinitionKey: 'someKey',
    processDefinitionVersions: ['someVersion'],
    tenantIds: ['a', 'b']
  });

  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('someKey', 'someVersion', 'a');
});

it('should load outlier data and flownode names when the process definition version changes', async () => {
  loadNodesOutliers.mockClear();
  getFlowNodeNames.mockClear();
  const node = shallow(<OutlierAnalysis />);
  const prevConfig = {processDefinitionKey: 'someKey', processDefinitionVersions: ['someVersion']};
  await node.instance().updateConfig(prevConfig);

  await node.instance().updateConfig({processDefinitionVersions: ['anotherVersion']});

  expect(getFlowNodeNames).toHaveBeenCalled();
  expect(loadNodesOutliers).toHaveBeenCalled();
});

it('should not try to load outlier data if no process definition is selected', async () => {
  loadNodesOutliers.mockClear();
  getFlowNodeNames.mockClear();

  const node = shallow(<OutlierAnalysis />);

  node.instance().updateConfig({});

  expect(loadNodesOutliers).not.toHaveBeenCalled();
});

it('should create correct flownodes higher outlier heat object', async () => {
  loadNodesOutliers.mockClear();
  getFlowNodeNames.mockClear();

  const node = shallow(<OutlierAnalysis />);

  await node.instance().updateConfig({
    processDefinitionKey: 'someKey',
    processDefinitionVersions: ['someVersion'],
    tenantIds: ['a', 'b']
  });

  expect(node.state().heatData).toEqual({nodeKey: 20});
});

it('display load chart data and display details modal when loadChartData is called', async () => {
  loadNodesOutliers.mockClear();
  getFlowNodeNames.mockClear();

  const node = shallow(<OutlierAnalysis />);

  await node.instance().updateConfig({
    processDefinitionKey: 'someKey',
    processDefinitionVersions: ['someVersion'],
    tenantIds: ['a', 'b']
  });

  const nodeData = {higherOutlier: {boundValue: 20}};

  await node.instance().loadChartData('nodeKey', nodeData);

  expect(loadDurationData).toHaveBeenCalled();
  expect(node.find('OutlierDetailsModal')).toExist();

  expect(node.state().selectedNode).toEqual({
    name: 'nodeName',
    id: 'nodeKey',
    ...nodeData,
    data: [{key: 'test', outlier: false, value: 'testVal'}]
  });
});

// ReEnable and complete test after OPT-2594 is done
// it('should display correct tooltip details', async () => {
//   loadNodesOutliers.mockClear();
//   getFlowNodeNames.mockClear();
//   loadProcessDefinitionXml.mockReturnValue('xml');

//   const node = shallow(<OutlierAnalysis />);

//   await node.instance().updateConfig({
//     processDefinitionKey: 'someKey',
//     processDefinitionVersions: ['someVersion'],
//     tenantIds: ['a', 'b']
//   });

// const tooltipNode = node.find('HeatmapOverlay').renderProp('formatter')({}, 'nodeKey');
// expect(tooltipNode).toMatchSnapshot();
// });
