/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {mount, shallow} from 'enzyme';

import {loadFrequencyData} from './service';

import Analysis from './Analysis';

import {incompatibleFilters, loadProcessDefinitionXml} from 'services';

jest.mock('./AnalysisControlPanel', () => () => <div>ControlPanel</div>);

jest.mock('components', () => {
  return {
    BPMNDiagram: () => <div>BPMNDiagram</div>,
    Message: () => <div>Message</div>
  };
});

jest.mock('./service', () => {
  return {
    loadFrequencyData: jest.fn()
  };
});

jest.mock('services', () => {
  return {
    loadProcessDefinitionXml: jest.fn(),
    loadDefinitions: () => [{key: 'key', versions: [{version: 2}, {version: 1}]}],
    incompatibleFilters: jest.fn()
  };
});

jest.mock('./DiagramBehavior', () => () => <div>DiagramBehavior</div>);
jest.mock('./Statistics', () => () => <div>Statistics</div>);

it('should contain a control panel', () => {
  const node = mount(<Analysis />);

  expect(node).toIncludeText('ControlPanel');
});

it('should load the process definition xml when the process definition id is updated', () => {
  const node = mount(<Analysis />);

  loadProcessDefinitionXml.mockClear();
  node.instance().updateConfig({
    processDefinitionKey: 'someKey',
    processDefinitionVersions: ['someVersion'],
    tenantIds: ['a', 'b']
  });

  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('someKey', 'someVersion', 'a');
});

it('should load frequency data when the process definition key changes', async () => {
  const node = mount(<Analysis />);

  node
    .instance()
    .updateConfig({processDefinitionKey: 'someKey', processDefinitionVersions: ['someVersion']});
  loadFrequencyData.mockClear();
  await node.instance().updateConfig({processDefinitionKey: 'anotherKey'});

  expect(loadFrequencyData.mock.calls[0][0]).toBe('anotherKey');
});

it('should load frequency data when the process definition version changes', async () => {
  const node = mount(<Analysis />);

  await node
    .instance()
    .updateConfig({processDefinitionKey: 'someKey', processDefinitionVersions: ['someVersion']});
  loadFrequencyData.mockClear();
  await node.instance().updateConfig({processDefinitionVersions: ['anotherVersion']});

  expect(loadFrequencyData.mock.calls[0][1]).toEqual(['anotherVersion']);
});

it('should load updated frequency data when the filter changed', async () => {
  const node = mount(<Analysis />);

  await node.instance().updateConfig({
    processDefinitionKey: 'someKey',
    processDefinitionVersions: ['someVersion'],
    tenantIds: [null]
  });
  loadFrequencyData.mockClear();
  await node.instance().updateConfig({filter: ['someFilter']});

  expect(loadFrequencyData.mock.calls[0][3]).toEqual(['someFilter']);
});

it('should not try to load frequency data if no process definition is selected', () => {
  const node = mount(<Analysis />);

  loadFrequencyData.mockClear();
  node.instance().updateConfig({filter: ['someFilter']});

  expect(loadFrequencyData).not.toHaveBeenCalled();
});

it('should contain a statistics section if gateway and endEvent is selected', () => {
  const node = mount(<Analysis />);

  node.instance().setState({
    gateway: 'g',
    endEvent: 'e'
  });

  expect(node).toIncludeText('Statistics');
});

it.only('should clear the selection when another process definition is selected', async () => {
  const node = mount(<Analysis />);

  await node.instance().setState({gateway: 'g', endEvent: 'e'});
  await node.instance().updateConfig({
    processDefinitionKey: 'newKey',
    processDefinitionVersions: ['latest'],
    tenantIds: []
  });

  expect(node).toHaveState('gateway', null);
  expect(node).toHaveState('endEvent', null);
  expect(node.state().config.filter).toEqual([]);
});

it('should show a warning message when there are incompatible filters', async () => {
  incompatibleFilters.mockReturnValue(true);
  const node = await mount(<Analysis />);
  await node.update();
  expect(node.find('Message')).toExist();
});

it('should not reset the xml when adding a filter', async () => {
  const node = shallow(<Analysis />);

  await node.instance().updateConfig({
    processDefinitionKey: 'someKey',
    processDefinitionVersions: ['someVersion'],
    tenantIds: ['a', 'b']
  });
  await node.instance().updateConfig({
    filter: [{type: 'completedInstancesOnly', data: null}]
  });

  expect(node.state().xml).not.toBe(null);
});
