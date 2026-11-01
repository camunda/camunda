/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';
import {Tabs} from '@carbon/react';

import {AgenticControlPlane} from './AgenticControlPlane';
import {AgentInventory} from './AgentInventory';
import {AgentView} from './AgentView';
import {DrillBreadcrumb} from './DrillBreadcrumb';
import {FilterBar} from './FilterBar';
import {ProcessFailures} from './ProcessFailures';
import {loadAgentDetail, loadAgenticDashboard} from './service';
import {TileSections} from './TileSections';

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn(() => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  })),
}));

jest.mock('notifications', () => ({showError: jest.fn()}));

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  loadDefinitions: jest.fn().mockReturnValue([{key: 'loan', name: 'Loan approval'}]),
}));

jest.mock('./service', () => ({
  loadAgenticDashboard: jest.fn(),
  loadAgentDetail: jest.fn(),
}));

const tiles = [
  {id: 'kpi-tile', configuration: {levels: ['overview', 'process', 'agent'], section: 'kpi'}},
];

const agentDetail = {
  agentId: 'loan:review',
  name: 'Review agent',
  agentType: 'AI_AGENT_SUB_PROCESS',
  processDefinitionKey: 'loan',
  processDefinitionName: 'Loan approval',
};

beforeEach(() => {
  jest.clearAllMocks();
  (loadAgenticDashboard as jest.Mock).mockReturnValue({tiles});
  (loadAgentDetail as jest.Mock).mockReturnValue(agentDetail);
});

type Node = ReturnType<typeof shallow>;

async function render() {
  const node = shallow(<AgenticControlPlane />);
  await runAllEffects();
  return node;
}

function filterBar(node: Node) {
  return node.find(FilterBar);
}

function selectTab(node: Node, selectedIndex: number) {
  (node.find(Tabs).prop('onChange') as (state: {selectedIndex: number}) => void)({selectedIndex});
}

async function selectProcess(node: Node, key: string | null) {
  (filterBar(node).prop('onProcessScopeChange') as (key: string | null) => void)(key);
  await runAllEffects();
}

async function selectAgents(node: Node, agentIds: string[]) {
  (filterBar(node).prop('onAgentIdsChange') as (ids: string[]) => void)(agentIds);
  await runAllEffects();
}

function breadcrumb(node: Node) {
  return node.find(DrillBreadcrumb);
}

it('should show a loading state while the dashboard is being fetched', () => {
  (loadAgenticDashboard as jest.Mock).mockReturnValue(undefined);
  const node = shallow(<AgenticControlPlane />);

  expect(node.find('Loading')).toExist();
});

it('should open on the dashboard tab at overview level', async () => {
  const node = await render();

  expect(loadAgenticDashboard).toHaveBeenCalled();
  expect(node.find(Tabs).prop('selectedIndex')).toBe(0);
  const sections = node.find(TileSections);
  expect(sections.prop('level')).toBe('overview');
  expect(sections.prop('sections')).toEqual(['kpi', 'trends']);
  expect(sections.prop('tiles')).toEqual(tiles);
  expect(sections.prop('definitions')).toEqual([]);
  expect(node.find(ProcessFailures)).not.toExist();
  expect(node.find(AgentInventory)).not.toExist();
});

it('should pass the date filter and its date unit to the tiles', async () => {
  const node = await render();

  (filterBar(node).prop('onPresetChange') as (v: string) => void)('7d');

  expect(node.find(TileSections).prop('filter')).toEqual([
    {
      type: 'instanceEndDate',
      filterLevel: 'instance',
      data: {
        type: 'rolling',
        start: {value: 7, unit: 'days'},
        end: null,
        excludeUndefined: false,
        includeUndefined: false,
      },
    },
  ]);
  expect(node.find(TileSections).prop('groupByDateUnit')).toBe('day');
});

it('should switch to the process level when a process is selected', async () => {
  const node = await render();

  await selectProcess(node, 'loan');
  (filterBar(node).prop('onVersionsChange') as (v: string[]) => void)(['3']);

  const sections = node.find(TileSections);
  expect(sections.prop('level')).toBe('process');
  expect(sections.prop('definitions')).toEqual([{key: 'loan', versions: ['3']}]);
  expect(node.find(ProcessFailures).prop('processDefinitionKey')).toBe('loan');
  expect(breadcrumb(node).prop('processName')).toBe('Loan approval');
  expect(breadcrumb(node).prop('rootLabelKey')).toBe('agenticControlPlane.breadcrumb.allProcesses');

  await selectProcess(node, null);

  expect(node.find(TileSections).prop('level')).toBe('overview');
  expect(node.find(ProcessFailures)).not.toExist();
});

it('should apply the model filter at the process level', async () => {
  const node = await render();

  await selectProcess(node, 'loan');
  (filterBar(node).prop('onModelsChange') as (v: string[]) => void)(['gpt-4o']);

  expect(filterBar(node).prop('showModelFilter')).toBeUndefined();
  expect(node.find(TileSections).prop('filter')).toContainEqual({
    type: 'agentRun',
    filterLevel: 'instance',
    data: {agentIds: [], models: ['gpt-4o'], providers: []},
  });
});

it('should reset version, agents and models when the process changes', async () => {
  const node = await render();

  await selectProcess(node, 'loan');
  (filterBar(node).prop('onVersionsChange') as (v: string[]) => void)(['3']);
  (filterBar(node).prop('onModelsChange') as (v: string[]) => void)(['gpt-4o']);
  await selectProcess(node, 'other');

  expect(filterBar(node).prop('versions')).toEqual(['all']);
  expect(filterBar(node).prop('models')).toEqual([]);
});

it('should show the inventory scoped to the selected process on the inventory tab', async () => {
  const node = await render();

  await selectProcess(node, 'loan');
  selectTab(node, 1);

  const inventory = node.find(AgentInventory);
  expect(inventory.prop('processDefinitionKey')).toBe('loan');
  expect(inventory.prop('processName')).toBe('Loan approval');
  expect(node.find(TileSections)).not.toExist();
});

it('should open the agent view from an inventory row and go back to the inventory', async () => {
  const node = await render();
  selectTab(node, 1);

  (node.find(AgentInventory).prop('onSelectAgent') as (id: string) => void)('loan:review');
  await runAllEffects();

  expect(loadAgentDetail).toHaveBeenCalledWith('loan:review', expect.any(Date));
  expect(node.find(AgentView).prop('agent')).toEqual(agentDetail);
  const sections = node.find(TileSections);
  expect(sections.prop('level')).toBe('agent');
  expect(sections.prop('sections')).toEqual(['kpi', 'agentTrends']);
  expect(sections.prop('definitions')).toEqual([{key: 'loan', versions: ['all']}]);
  expect(sections.prop('filter')).toContainEqual({
    type: 'agentRun',
    filterLevel: 'instance',
    data: {agentIds: ['loan:review'], models: [], providers: []},
  });
  expect(breadcrumb(node).prop('rootLabelKey')).toBe(
    'agenticControlPlane.breadcrumb.agentInventory'
  );
  expect(breadcrumb(node).prop('agentName')).toBe('Review agent');
  expect(breadcrumb(node).prop('processName')).toBe('Loan approval');

  (breadcrumb(node).prop('onRoot') as () => void)();
  await runAllEffects();

  expect(node.find(AgentView)).not.toExist();
  expect(node.find(AgentInventory)).toExist();
  expect(node.find(AgentInventory).prop('processDefinitionKey')).toBe(null);
});

it('should scope the inventory to the agent process from the process crumb', async () => {
  const node = await render();
  selectTab(node, 1);
  (node.find(AgentInventory).prop('onSelectAgent') as (id: string) => void)('loan:review');
  await runAllEffects();

  (breadcrumb(node).prop('onProcess') as () => void)();
  await runAllEffects();

  expect(node.find(AgentInventory).prop('processDefinitionKey')).toBe('loan');
  expect(filterBar(node).prop('processScope')).toBe('loan');
});

it('should open the agent view when one agent is picked in the agent filter', async () => {
  const node = await render();
  await selectProcess(node, 'loan');
  (filterBar(node).prop('onVersionsChange') as (v: string[]) => void)(['2']);

  await selectAgents(node, ['loan:review']);

  expect(node.find(AgentView)).toExist();
  expect(node.find(TileSections).prop('definitions')).toEqual([{key: 'loan', versions: ['2']}]);
  expect(breadcrumb(node).prop('rootLabelKey')).toBe('agenticControlPlane.breadcrumb.allProcesses');

  (breadcrumb(node).prop('onProcess') as () => void)();
  await runAllEffects();

  expect(node.find(AgentView)).not.toExist();
  expect(node.find(TileSections).prop('level')).toBe('process');
  expect(filterBar(node).prop('versions')).toEqual(['2']);
});

it('should keep the dashboard when several agents are picked in the agent filter', async () => {
  const node = await render();

  await selectAgents(node, ['loan:review', 'loan:triage']);

  expect(node.find(AgentView)).not.toExist();
  expect(node.find(TileSections).prop('level')).toBe('overview');
  expect(node.find(TileSections).prop('filter')).toContainEqual({
    type: 'agentRun',
    filterLevel: 'instance',
    data: {agentIds: ['loan:review', 'loan:triage'], models: [], providers: []},
  });
});

it('should open the agent view from the process failures table', async () => {
  const node = await render();
  await selectProcess(node, 'loan');

  (node.find(ProcessFailures).prop('onSelectAgent') as (id: string) => void)('loan:review');
  await runAllEffects();

  expect(node.find(AgentView)).toExist();
  expect(node.find(Tabs).prop('selectedIndex')).toBe(0);
});

it('should leave the agent view when switching tabs', async () => {
  const node = await render();
  await selectAgents(node, ['loan:review']);
  expect(node.find(AgentView)).toExist();

  selectTab(node, 1);

  expect(node.find(AgentView)).not.toExist();
  expect(node.find(AgentInventory)).toExist();
});
