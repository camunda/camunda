/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';

import {evaluateReport} from 'services';

import {AgenticControlPlane} from './AgenticControlPlane';
import {loadAgenticDashboard} from './service';

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn(() => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  })),
}));

jest.mock('notifications', () => ({showError: jest.fn()}));

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  evaluateReport: jest.fn(),
}));

jest.mock('./service', () => ({
  loadAgenticDashboard: jest.fn().mockReturnValue({tiles: [], availableFilters: []}),
}));

beforeEach(() => {
  jest.clearAllMocks();
  (loadAgenticDashboard as jest.Mock).mockReturnValue({tiles: [], availableFilters: []});
});

it('should show a loading state while the dashboard is being fetched', () => {
  const node = shallow(<AgenticControlPlane />);

  expect(node.find('Loading')).toExist();
});

it('should load the agentic dashboard on mount', async () => {
  shallow(<AgenticControlPlane />);

  await runAllEffects();

  expect(loadAgenticDashboard).toHaveBeenCalled();
});

it('should render the dashboard once tiles are loaded', async () => {
  const node = shallow(<AgenticControlPlane />);

  await runAllEffects();

  expect(node.find('DashboardRenderer')).toExist();
  expect(node.find('Loading')).not.toExist();
});

it('should pass the default Last 30 days filter to DashboardRenderer', async () => {
  const node = shallow(<AgenticControlPlane />);

  await runAllEffects();

  const expectedFilter = [
    {
      type: 'instanceEndDate',
      filterLevel: 'instance',
      data: {
        type: 'rolling',
        start: {value: 30, unit: 'days'},
        end: null,
        excludeUndefined: false,
        includeUndefined: false,
      },
    },
  ];
  expect(node.find('.agentHealth-section').find('DashboardRenderer').prop('filter')).toEqual(
    expectedFilter
  );
  expect(node.find('.costAndUsage-section').find('DashboardRenderer').prop('filter')).toEqual(
    expectedFilter
  );
});

it('should update the filter when the date preset changes', async () => {
  const node = shallow(<AgenticControlPlane />);

  await runAllEffects();

  (node.find('FilterBar').prop('onPresetChange') as (v: string) => void)('7d');

  const expectedFilter = [
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
  ];
  expect(node.find('.agentHealth-section').find('DashboardRenderer').prop('filter')).toEqual(
    expectedFilter
  );
  expect(node.find('.costAndUsage-section').find('DashboardRenderer').prop('filter')).toEqual(
    expectedFilter
  );
});

it('should render the page header with title and description', async () => {
  const node = shallow(<AgenticControlPlane />);

  await runAllEffects();

  expect(node.find('.title')).toExist();
  expect(node.find('.description')).toExist();
});

it('should pass the loaded tiles to DashboardRenderer', async () => {
  const tiles = [{id: 'report-1', position: {x: 0, y: 0}, dimensions: {width: 4, height: 3}}];
  (loadAgenticDashboard as jest.Mock).mockReturnValueOnce({tiles, availableFilters: []});

  const node = shallow(<AgenticControlPlane />);

  await runAllEffects();

  expect(node.find('.agentHealth-section').find('DashboardRenderer').prop('tiles')).toEqual(tiles);
});

const levelTiles = [
  {id: 'overview-tile', configuration: {levels: ['overview']}, position: {x: 0, y: 0}},
  {id: 'process-tile', configuration: {levels: ['process']}, position: {x: 1, y: 0}},
  {id: 'agent-tile', configuration: {levels: ['overview', 'agent']}, position: {x: 2, y: 0}},
];

function tileIds(node: ReturnType<typeof shallow>, section: string) {
  return (
    node.find(`.${section}-section`).find('DashboardRenderer').prop('tiles') as {id: string}[]
  ).map(({id}) => id);
}

it('should show overview tiles in the Phase 2 sections when no process is selected', async () => {
  (loadAgenticDashboard as jest.Mock).mockReturnValueOnce({
    tiles: levelTiles,
    availableFilters: [],
  });

  const node = shallow(<AgenticControlPlane />);
  await runAllEffects();

  expect(tileIds(node, 'agentHealth')).toEqual(['overview-tile', 'agent-tile']);
  expect(node.find('.kpi-section')).not.toExist();
  expect(node.find('DrillBreadcrumb').prop('processScope')).toBe(null);
});

it('should switch to the Phase 1 process sections once a process is selected', async () => {
  (loadAgenticDashboard as jest.Mock).mockReturnValueOnce({
    tiles: levelTiles,
    availableFilters: [],
  });

  const node = shallow(<AgenticControlPlane />);
  await runAllEffects();

  (node.find('FilterBar').prop('onProcessScopeChange') as (v: string) => void)('my-process');

  expect(tileIds(node, 'kpi')).toEqual(['process-tile']);
  expect(node.find('.token-section')).toExist();
  expect(node.find('.agentHealth-section')).not.toExist();
  expect(node.find('FilterBar').prop('showModelFilter')).toBe(false);
});

it('should show agent tiles once an agent of the selected process is selected', async () => {
  (loadAgenticDashboard as jest.Mock).mockReturnValueOnce({
    tiles: levelTiles,
    availableFilters: [],
  });

  const node = shallow(<AgenticControlPlane />);
  await runAllEffects();

  (node.find('FilterBar').prop('onProcessScopeChange') as (v: string) => void)('my-process');
  (node.find('FilterBar').prop('onAgentIdsChange') as (v: string[]) => void)(['loan:review']);

  expect(tileIds(node, 'agentHealth')).toEqual(['agent-tile']);
  expect(node.find('.kpi-section')).not.toExist();
  expect(node.find('FilterBar').prop('showModelFilter')).toBe(true);
});

it('should go back up a level from the breadcrumb', async () => {
  (loadAgenticDashboard as jest.Mock).mockReturnValueOnce({
    tiles: levelTiles,
    availableFilters: [],
  });

  const node = shallow(<AgenticControlPlane />);
  await runAllEffects();

  (node.find('FilterBar').prop('onProcessScopeChange') as (v: string) => void)('my-process');
  (node.find('FilterBar').prop('onAgentIdsChange') as (v: string[]) => void)(['loan:review']);
  (node.find('DrillBreadcrumb').prop('onProcess') as () => void)();

  expect(tileIds(node, 'kpi')).toEqual(['process-tile']);

  (node.find('DrillBreadcrumb').prop('onOverview') as () => void)();

  expect(tileIds(node, 'agentHealth')).toEqual(['overview-tile', 'agent-tile']);
});

it('should include definitions in evaluate calls when a process is selected', async () => {
  const node = shallow(<AgenticControlPlane />);
  await runAllEffects();

  (node.find('FilterBar').prop('onProcessScopeChange') as (v: string) => void)('my-process');

  const loadTile = node.find('.kpi-section').find('DashboardRenderer').prop('loadTile') as (
    id: string,
    filter: unknown[],
    params: unknown
  ) => void;
  loadTile('report-id', [], {});

  expect(evaluateReport).toHaveBeenCalledWith('report-id', [], {}, [
    {key: 'my-process', versions: ['all']},
  ]);
});

it('should pass empty definitions in evaluate calls when no process is selected', async () => {
  const node = shallow(<AgenticControlPlane />);
  await runAllEffects();

  const loadTile = node.find('.agentHealth-section').find('DashboardRenderer').prop('loadTile') as (
    id: string,
    filter: unknown[],
    params: unknown
  ) => void;
  loadTile('report-id', [], {});

  expect(evaluateReport).toHaveBeenCalledWith('report-id', [], {}, []);
});

it('should include the selected version in evaluate definitions', async () => {
  const node = shallow(<AgenticControlPlane />);
  await runAllEffects();

  (node.find('FilterBar').prop('onProcessScopeChange') as (v: string) => void)('my-process');
  (node.find('FilterBar').prop('onVersionsChange') as (v: string[]) => void)(['3']);

  const loadTile = node.find('.kpi-section').find('DashboardRenderer').prop('loadTile') as (
    id: string,
    filter: unknown[],
    params: unknown
  ) => void;
  loadTile('report-id', [], {});

  expect(evaluateReport).toHaveBeenCalledWith('report-id', [], {}, [
    {key: 'my-process', versions: ['3']},
  ]);
});

it('should pass the selected versions to FilterBar', async () => {
  const node = shallow(<AgenticControlPlane />);
  await runAllEffects();

  (node.find('FilterBar').prop('onProcessScopeChange') as (v: string) => void)('my-process');
  (node.find('FilterBar').prop('onVersionsChange') as (v: string[]) => void)(['2']);

  expect(node.find('FilterBar').prop('versions')).toEqual(['2']);
});

it('should reset the version to all when the process scope changes', async () => {
  const node = shallow(<AgenticControlPlane />);
  await runAllEffects();

  (node.find('FilterBar').prop('onProcessScopeChange') as (v: string) => void)('my-process');
  (node.find('FilterBar').prop('onVersionsChange') as (v: string[]) => void)(['3']);
  expect(node.find('FilterBar').prop('versions')).toEqual(['3']);

  // selecting a different process must reset the version filter
  (node.find('FilterBar').prop('onProcessScopeChange') as (v: string) => void)('other-process');

  expect(node.find('FilterBar').prop('versions')).toEqual(['all']);

  const loadTile = node.find('.kpi-section').find('DashboardRenderer').prop('loadTile') as (
    id: string,
    filter: unknown[],
    params: unknown
  ) => void;
  loadTile('report-id', [], {});

  expect(evaluateReport).toHaveBeenCalledWith('report-id', [], {}, [
    {key: 'other-process', versions: ['all']},
  ]);
});

it('should render a volume tile without levels at the overview only', async () => {
  const tiles = [
    {
      id: 'tool-calls',
      configuration: {section: 'volume'},
      position: {x: 0, y: 6},
      dimensions: {width: 18, height: 2},
    },
  ];
  (loadAgenticDashboard as jest.Mock).mockReturnValueOnce({tiles, availableFilters: []});

  const node = shallow(<AgenticControlPlane />);
  await runAllEffects();

  expect(tileIds(node, 'volume')).toEqual(['tool-calls']);

  (node.find('FilterBar').prop('onProcessScopeChange') as (v: string) => void)('my-process');

  expect(node.find('.volume-section')).not.toExist();
});

it('should inject the configured top-N limit when evaluating a tile with a topN config', async () => {
  const tiles = [
    {id: 'consumers', configuration: {section: 'costAndUsage', topN: '10'}, position: {x: 0, y: 0}},
  ];
  (loadAgenticDashboard as jest.Mock).mockReturnValueOnce({tiles, availableFilters: []});

  const node = shallow(<AgenticControlPlane />);
  await runAllEffects();

  const loadTile = node
    .find('.costAndUsage-section')
    .find('DashboardRenderer')
    .prop('loadTile') as (id: string, filter: unknown[], params: unknown) => void;
  loadTile('consumers', [], {});

  expect(evaluateReport).toHaveBeenCalledWith('consumers', [], {limit: 10}, [], 'week');
});

it('should not inject a limit for token tiles without a topN config', async () => {
  const tiles = [
    {id: 'token-trend', configuration: {section: 'costAndUsage'}, position: {x: 0, y: 0}},
  ];
  (loadAgenticDashboard as jest.Mock).mockReturnValueOnce({tiles, availableFilters: []});

  const node = shallow(<AgenticControlPlane />);
  await runAllEffects();

  const loadTile = node
    .find('.costAndUsage-section')
    .find('DashboardRenderer')
    .prop('loadTile') as (id: string, filter: unknown[], params: unknown) => void;
  loadTile('token-trend', [], {});

  expect(evaluateReport).toHaveBeenCalledWith('token-trend', [], {}, [], 'week');
});

it('should add an agent run filter for the selected agents and models', async () => {
  const node = shallow(<AgenticControlPlane />);

  await runAllEffects();

  (node.find('FilterBar').prop('onAgentIdsChange') as (v: string[]) => void)(['loan:review']);
  (node.find('FilterBar').prop('onModelsChange') as (v: string[]) => void)(['gpt-4o']);

  const filter = node
    .find('.agentHealth-section')
    .find('DashboardRenderer')
    .prop('filter') as unknown as {
    type: string;
  }[];
  expect(filter).toContainEqual({
    type: 'agentRun',
    filterLevel: 'instance',
    data: {agentIds: ['loan:review'], models: ['gpt-4o'], providers: []},
  });
});

it('should clear the agent and model selection when the process scope changes', async () => {
  const node = shallow(<AgenticControlPlane />);

  await runAllEffects();

  (node.find('FilterBar').prop('onAgentIdsChange') as (v: string[]) => void)(['loan:review']);
  (node.find('FilterBar').prop('onProcessScopeChange') as (v: string) => void)('other-process');

  expect(node.find('FilterBar').prop('agentIds')).toEqual([]);
  const filter = node.find('.kpi-section').find('DashboardRenderer').prop('filter') as unknown as {
    type: string;
  }[];
  expect(filter.map(({type}) => type)).toEqual(['instanceEndDate']);
});

it('should render the three Phase 2 sections', async () => {
  const node = shallow(<AgenticControlPlane />);

  await runAllEffects();

  expect(node.find('.agentHealth-section')).toExist();
  expect(node.find('.volume-section')).toExist();
  expect(node.find('.costAndUsage-section')).toExist();
});

it('should show process KPIs in the processes section on the overview only', async () => {
  const tiles = [
    {
      id: 'completed',
      configuration: {levels: ['overview', 'process'], sectionByLevel: {overview: 'processes'}},
      position: {x: 0, y: 0},
    },
    {
      id: 'consumers',
      configuration: {levels: ['overview'], section: 'processes', topN: '10'},
      position: {x: 0, y: 2},
    },
  ];
  (loadAgenticDashboard as jest.Mock).mockReturnValueOnce({tiles, availableFilters: []});

  const node = shallow(<AgenticControlPlane />);
  await runAllEffects();

  expect(tileIds(node, 'processes')).toEqual(['completed', 'consumers']);

  (node.find('FilterBar').prop('onProcessScopeChange') as (v: string) => void)('my-process');

  expect(node.find('.processes-section')).not.toExist();
  expect(tileIds(node, 'kpi')).toEqual(['completed']);

  (node.find('FilterBar').prop('onAgentIdsChange') as (v: string[]) => void)(['loan:review']);

  expect(node.find('.processes-section')).not.toExist();
});
