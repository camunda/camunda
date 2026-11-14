/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';
import {ContentSwitcher, Search} from '@carbon/react';

import {AgentInventory, inventoryColumns, sortInventory} from './AgentInventory';
import {loadAgentInventory, type AgentInventoryEntry} from './service';

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn(() => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  })),
}));

jest.mock('notifications', () => ({showError: jest.fn()}));

jest.mock('./service', () => ({loadAgentInventory: jest.fn()}));

function entry(overrides: Partial<AgentInventoryEntry>): AgentInventoryEntry {
  return {
    agentId: 'loan:review',
    name: 'Review agent',
    agentType: 'AI_AGENT_SUB_PROCESS',
    processDefinitionKey: 'loan',
    processDefinitionName: 'Loan Approval',
    models: ['gpt-4o'],
    runs: 10,
    totalTokens: 5000,
    avgTokens: 500,
    durationP50Ms: 1000,
    durationP95Ms: 3000,
    incidentRate: 10,
    lastRunDate: '2026-10-20T10:00:00.000Z',
    ...overrides,
  };
}

const entries = [
  entry({agentId: 'a', name: 'Alpha', runs: 3, incidentRate: 50, models: ['claude']}),
  entry({agentId: 'b', name: 'Beta', runs: 0, incidentRate: null, agentType: 'EXTERNAL_AGENT'}),
  entry({agentId: 'c', name: 'Gamma', runs: 12, incidentRate: 95, processDefinitionName: 'Claims'}),
];

const summary = {agents: 3, processes: 2, externalAgents: 1};

beforeEach(() => {
  jest.clearAllMocks();
  (loadAgentInventory as jest.Mock).mockReturnValue({summary, agents: entries});
});

function render(props: Partial<Parameters<typeof AgentInventory>[0]> = {}) {
  return shallow(
    <AgentInventory
      endedFrom={new Date('2026-10-01T00:00:00.000Z')}
      processDefinitionKey={null}
      processName={null}
      agentIds={[]}
      models={[]}
      onSelectAgent={jest.fn()}
      {...props}
    />
  );
}

function names(node: ReturnType<typeof shallow>) {
  return node.find('.agentName').map((name) => name.text());
}

it('should sort and keep agents without a value last in both directions', () => {
  expect(sortInventory(entries, 'runs', 'DESC').map(({agentId}) => agentId)).toEqual([
    'c',
    'a',
    'b',
  ]);
  expect(sortInventory(entries, 'incidentRate', 'DESC').map(({agentId}) => agentId)).toEqual([
    'c',
    'a',
    'b',
  ]);
  expect(sortInventory(entries, 'incidentRate', 'ASC').map(({agentId}) => agentId)).toEqual([
    'a',
    'c',
    'b',
  ]);
});

it('should load the inventory for the selected process and show the summary', async () => {
  const node = render({processDefinitionKey: 'loan', processName: 'Loan Approval'});
  await runAllEffects();

  expect(loadAgentInventory).toHaveBeenCalledWith(new Date('2026-10-01T00:00:00.000Z'), 'loan');
  expect(node.find('.section-title').text()).toContain('Agents in Loan Approval');
  expect(node.find('.summary').text()).toBe('3 agents across 2 processes, 1 external');
});

it('should list agents by volume and switch the sort when a header is clicked', async () => {
  const node = render();
  await runAllEffects();

  expect(names(node)).toEqual(['Gamma', 'Alpha', 'Beta']);

  node.find('TableHeader').at(0).simulate('click');

  expect(names(node)).toEqual(['Alpha', 'Beta', 'Gamma']);
});

it('should filter agents by the search text across name, process and model', async () => {
  const node = render();
  await runAllEffects();

  node.find(Search).simulate('change', {target: {value: 'claims'}});
  expect(names(node)).toEqual(['Gamma']);

  node.find(Search).simulate('change', {target: {value: 'claude'}});
  expect(names(node)).toEqual(['Alpha']);

  node.find(Search).simulate('change', {target: {value: 'nothing'}});
  expect(names(node)).toEqual([]);
  expect(node.find('TableCell.empty')).toExist();
});

it('should apply the agent and model filters', async () => {
  const node = render({models: ['claude']});
  await runAllEffects();
  expect(names(node)).toEqual(['Alpha']);

  const byAgent = render({agentIds: ['b', 'c']});
  await runAllEffects();
  expect(names(byAgent)).toEqual(['Gamma', 'Beta']);
});

it('should open the agent of a clicked row', async () => {
  const onSelectAgent = jest.fn();
  const node = render({onSelectAgent});
  await runAllEffects();

  node.find('.drillable').at(0).simulate('click');

  expect(onSelectAgent).toHaveBeenCalledWith('c');
});

it('should show one duration column that switches between P50 and P95', async () => {
  const node = render();
  await runAllEffects();

  const headers = () => node.find('TableHeader').map((header) => header.prop('children'));
  expect(headers()).toContain('Duration (P50)');
  expect(headers()).not.toContain('Duration (P95)');

  node.find(ContentSwitcher).simulate('change', {name: 'P95'});

  expect(headers()).toContain('Duration (P95)');
  expect(headers()).not.toContain('Duration (P50)');
});

it('should show the columns of the inventory with the model most runs used', () => {
  const columns = inventoryColumns('P50');

  expect(columns.map(({titleKey}) => titleKey)).toEqual([
    'agenticControlPlane.inventory.agent',
    'agenticControlPlane.inventory.process',
    'agenticControlPlane.inventory.source',
    'agenticControlPlane.inventory.model',
    'agenticControlPlane.inventory.runs',
    'agenticControlPlane.inventory.totalTokens',
    'agenticControlPlane.inventory.tokensPerRun',
    'agenticControlPlane.inventory.incidentRate',
    'agenticControlPlane.inventory.durationP50',
    'agenticControlPlane.inventory.lastRun',
  ]);
  const model = columns.find(({titleKey}) => titleKey.endsWith('.model'))!;
  expect(model.render(entry({models: ['claude', 'gpt-4o']}))).toBe('claude');
});
