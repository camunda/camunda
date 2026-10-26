/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';

import {AgentInventory, sortInventory} from './AgentInventory';
import {loadAgentInventory, type AgentInventoryEntry} from './service';

jest.mock('./service', () => ({loadAgentInventory: jest.fn()}));

function entry(overrides: Partial<AgentInventoryEntry>): AgentInventoryEntry {
  return {
    agentId: 'loan:review',
    name: 'Review agent',
    agentType: 'aiAgentSubProcess',
    processDefinitionKey: 'loan',
    processDefinitionName: 'Loan Approval',
    models: ['gpt-4o'],
    maxModelCalls: 5,
    runs: 10,
    incidentRate: 10,
    limitHitRate: 0,
    durationP50Ms: 1000,
    avgTokens: 500,
    lastRunDate: '2026-10-20T10:00:00.000Z',
    ...overrides,
  };
}

const entries = [
  entry({agentId: 'a', name: 'Alpha', runs: 3, incidentRate: 50}),
  entry({agentId: 'b', name: 'Beta', runs: 0, incidentRate: null}),
  entry({agentId: 'c', name: 'Gamma', runs: 12, incidentRate: 5}),
];

it('should sort by runs and keep agents without a value last in both directions', () => {
  expect(sortInventory(entries, 'runs', 'DESC').map(({agentId}) => agentId)).toEqual([
    'c',
    'a',
    'b',
  ]);
  expect(sortInventory(entries, 'incidentRate', 'DESC').map(({agentId}) => agentId)).toEqual([
    'a',
    'c',
    'b',
  ]);
  expect(sortInventory(entries, 'incidentRate', 'ASC').map(({agentId}) => agentId)).toEqual([
    'c',
    'a',
    'b',
  ]);
});

it('should list agents by volume and switch the sort when a header is clicked', async () => {
  (loadAgentInventory as jest.Mock).mockResolvedValue(entries);
  const node = shallow(<AgentInventory endedFrom={new Date()} onSelectAgent={jest.fn()} />);
  await runAllEffects();
  await flushPromises();

  expect(node.find('.agentName').map((name) => name.text())).toEqual(['Gamma', 'Alpha', 'Beta']);

  node.find('TableHeader').at(0).simulate('click');

  expect(node.find('.agentName').map((name) => name.text())).toEqual(['Alpha', 'Beta', 'Gamma']);
});

it('should drill into the agent of a clicked row', async () => {
  (loadAgentInventory as jest.Mock).mockResolvedValue([entry({})]);
  const onSelectAgent = jest.fn();
  const node = shallow(<AgentInventory endedFrom={new Date()} onSelectAgent={onSelectAgent} />);
  await runAllEffects();
  await flushPromises();

  node.find('.drillable').simulate('click');

  expect(onSelectAgent).toHaveBeenCalledWith('loan', 'loan:review');
});

function flushPromises() {
  return new Promise((resolve) => setTimeout(resolve, 0));
}
