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
  const node = shallow(<AgenticControlPlane/>);

  expect(node.find('Loading')).toExist();
});

it('should load the agentic dashboard on mount', async () => {
  shallow(<AgenticControlPlane/>);

  await runAllEffects();

  expect(loadAgenticDashboard).toHaveBeenCalled();
});

it('should render the dashboard once tiles are loaded', async () => {
  const node = shallow(<AgenticControlPlane/>);

  await runAllEffects();

  expect(node.find('DashboardRenderer')).toExist();
  expect(node.find('Loading')).not.toExist();
});

it('should pass the default Last 30 days filter to DashboardRenderer', async () => {
  const node = shallow(<AgenticControlPlane/>);

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
  expect(node.find('.kpi-section').find('DashboardRenderer').prop('filter')).toEqual(
    expectedFilter
  );
  expect(node.find('.token-section').find('DashboardRenderer').prop('filter')).toEqual(
    expectedFilter
  );
});

it('should update the filter when the date preset changes', async () => {
  const node = shallow(<AgenticControlPlane/>);

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
  expect(node.find('.kpi-section').find('DashboardRenderer').prop('filter')).toEqual(
    expectedFilter
  );
  expect(node.find('.token-section').find('DashboardRenderer').prop('filter')).toEqual(
    expectedFilter
  );
});

it('should render the page header with title and description', async () => {
  const node = shallow(<AgenticControlPlane/>);

  await runAllEffects();

  expect(node.find('.title')).toExist();
  expect(node.find('.description')).toExist();
});

it('should pass the loaded tiles to DashboardRenderer', async () => {
  const tiles = [{id: 'report-1', position: {x: 0, y: 0}, dimensions: {width: 4, height: 3}}];
  (loadAgenticDashboard as jest.Mock).mockReturnValueOnce({tiles, availableFilters: []});

  const node = shallow(<AgenticControlPlane/>);

  await runAllEffects();

  expect(node.find('.kpi-section').find('DashboardRenderer').prop('tiles')).toEqual(tiles);
});

it('should hide visibleInL0Only tiles when a process is selected', async () => {
  const tiles = [
    {id: 'l0-tile', configuration: {visibleInL0Only: true}, position: {x: 0, y: 0}},
    {id: 'l1-tile', configuration: {visibleInL1Only: true}, position: {x: 1, y: 0}},
    {id: 'common-tile', configuration: {}, position: {x: 2, y: 0}},
  ];
  (loadAgenticDashboard as jest.Mock).mockReturnValueOnce({tiles, availableFilters: []});

  const node = shallow(<AgenticControlPlane/>);
  await runAllEffects();

  (node.find('FilterBar').prop('onProcessScopeChange') as (v: string) => void)('my-process');

  const visibleTiles = node.find('.kpi-section').find('DashboardRenderer').prop('tiles') as {
    id: string;
  }[];
  expect(visibleTiles.map((t) => t.id)).toEqual(['l1-tile', 'common-tile']);
});

it('should hide visibleInL1Only tiles when no process is selected (L0)', async () => {
  const tiles = [
    {id: 'l0-tile', configuration: {visibleInL0Only: true}, position: {x: 0, y: 0}},
    {id: 'l1-tile', configuration: {visibleInL1Only: true}, position: {x: 1, y: 0}},
    {id: 'common-tile', configuration: {}, position: {x: 2, y: 0}},
  ];
  (loadAgenticDashboard as jest.Mock).mockReturnValueOnce({tiles, availableFilters: []});

  const node = shallow(<AgenticControlPlane/>);
  await runAllEffects();

  const visibleTiles = node.find('.kpi-section').find('DashboardRenderer').prop('tiles') as {
    id: string;
  }[];
  expect(visibleTiles.map((t) => t.id)).toEqual(['l0-tile', 'common-tile']);
});

it('should include definitions in evaluate calls when a process is selected', async () => {
  const node = shallow(<AgenticControlPlane/>);
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
  const node = shallow(<AgenticControlPlane/>);
  await runAllEffects();

  const loadTile = node.find('.kpi-section').find('DashboardRenderer').prop('loadTile') as (
    id: string,
    filter: unknown[],
    params: unknown
  ) => void;
  loadTile('report-id', [], {});

  expect(evaluateReport).toHaveBeenCalledWith('report-id', [], {}, []);
});

it('should render a reliabilityAndToolCalls tile in its section in both L0 and L1', async () => {
  // the Total tool calls tile carries no L0/L1-only flag, so it must show in both scopes
  const tiles = [
    {
      id: 'tool-calls',
      configuration: {section: 'reliabilityAndToolCalls'},
      position: {x: 0, y: 6},
      dimensions: {width: 18, height: 2},
    },
  ];
  (loadAgenticDashboard as jest.Mock).mockReturnValueOnce({tiles, availableFilters: []});

  const node = shallow(<AgenticControlPlane/>);
  await runAllEffects();

  // L0 (fleet view — no process selected)
  expect(
    (
      node.find('.reliabilityAndToolCalls-section').find('DashboardRenderer').prop('tiles') as {
        id: string;
      }[]
    ).map((t) => t.id)
  ).toEqual(['tool-calls']);

  // L1 (process drill-down)
  (node.find('FilterBar').prop('onProcessScopeChange') as (v: string) => void)('my-process');

  expect(
    (
      node.find('.reliabilityAndToolCalls-section').find('DashboardRenderer').prop('tiles') as {
        id: string;
      }[]
    ).map((t) => t.id)
  ).toEqual(['tool-calls']);
});

it('should inject the configured top-N limit when evaluating a tile with a topN config', async () => {
  const tiles = [
    {id: 'consumers', configuration: {section: 'token', topN: '10'}, position: {x: 0, y: 0}},
  ];
  (loadAgenticDashboard as jest.Mock).mockReturnValueOnce({tiles, availableFilters: []});

  const node = shallow(<AgenticControlPlane/>);
  await runAllEffects();

  const loadTile = node.find('.token-section').find('DashboardRenderer').prop('loadTile') as (
    id: string,
    filter: unknown[],
    params: unknown
  ) => void;
  loadTile('consumers', [], {});

  expect(evaluateReport).toHaveBeenCalledWith('consumers', [], {limit: 10}, [], 'week');
});

it('should not inject a limit for token tiles without a topN config', async () => {
  const tiles = [{id: 'token-trend', configuration: {section: 'token'}, position: {x: 0, y: 0}}];
  (loadAgenticDashboard as jest.Mock).mockReturnValueOnce({tiles, availableFilters: []});

  const node = shallow(<AgenticControlPlane/>);
  await runAllEffects();

  const loadTile = node.find('.token-section').find('DashboardRenderer').prop('loadTile') as (
    id: string,
    filter: unknown[],
    params: unknown
  ) => void;
  loadTile('token-trend', [], {});

  expect(evaluateReport).toHaveBeenCalledWith('token-trend', [], {}, [], 'week');
});
