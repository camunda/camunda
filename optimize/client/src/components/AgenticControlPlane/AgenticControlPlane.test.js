/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from 'react';
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
  loadAgenticDashboard.mockReturnValue({tiles: [], availableFilters: []});
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
  expect(node.find('.AgenticControlPlane__kpi-section').find('DashboardRenderer').prop('filter')).toEqual(expectedFilter);
  expect(node.find('.AgenticControlPlane__token-section').find('DashboardRenderer').prop('filter')).toEqual(expectedFilter);
});

it('should update the filter when the date preset changes', async () => {
  const node = shallow(<AgenticControlPlane />);

  await runAllEffects();

  node.find('FilterBar').prop('onPresetChange')('7d');

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
  expect(node.find('.AgenticControlPlane__kpi-section').find('DashboardRenderer').prop('filter')).toEqual(expectedFilter);
  expect(node.find('.AgenticControlPlane__token-section').find('DashboardRenderer').prop('filter')).toEqual(expectedFilter);
});

it('should render the page header with title and description', async () => {
  const node = shallow(<AgenticControlPlane />);

  await runAllEffects();

  expect(node.find('.AgenticControlPlane__title')).toExist();
  expect(node.find('.AgenticControlPlane__description')).toExist();
});

it('should pass the loaded tiles to DashboardRenderer', async () => {
  const tiles = [{id: 'report-1', position: {x: 0, y: 0}, dimensions: {width: 4, height: 3}}];
  loadAgenticDashboard.mockReturnValueOnce({tiles, availableFilters: []});

  const node = shallow(<AgenticControlPlane />);

  await runAllEffects();

  expect(node.find('.AgenticControlPlane__kpi-section').find('DashboardRenderer').prop('tiles')).toEqual(tiles);
});

it('should hide visibleInL0Only tiles when a process is selected', async () => {
  const tiles = [
    {id: 'l0-tile', configuration: {visibleInL0Only: true}, position: {x: 0, y: 0}},
    {id: 'l1-tile', configuration: {visibleInL1Only: true}, position: {x: 1, y: 0}},
    {id: 'common-tile', configuration: {}, position: {x: 2, y: 0}},
  ];
  loadAgenticDashboard.mockReturnValueOnce({tiles, availableFilters: []});

  const node = shallow(<AgenticControlPlane />);
  await runAllEffects();

  node.find('FilterBar').prop('onProcessScopeChange')('my-process');

  const visibleTiles = node.find('.AgenticControlPlane__kpi-section').find('DashboardRenderer').prop('tiles');
  expect(visibleTiles.map((t) => t.id)).toEqual(['l1-tile', 'common-tile']);
});

it('should hide visibleInL1Only tiles when no process is selected (L0)', async () => {
  const tiles = [
    {id: 'l0-tile', configuration: {visibleInL0Only: true}, position: {x: 0, y: 0}},
    {id: 'l1-tile', configuration: {visibleInL1Only: true}, position: {x: 1, y: 0}},
    {id: 'common-tile', configuration: {}, position: {x: 2, y: 0}},
  ];
  loadAgenticDashboard.mockReturnValueOnce({tiles, availableFilters: []});

  const node = shallow(<AgenticControlPlane />);
  await runAllEffects();

  const visibleTiles = node.find('.AgenticControlPlane__kpi-section').find('DashboardRenderer').prop('tiles');
  expect(visibleTiles.map((t) => t.id)).toEqual(['l0-tile', 'common-tile']);
});

it('should include definitions in evaluate calls when a process is selected', async () => {
  const node = shallow(<AgenticControlPlane />);
  await runAllEffects();

  node.find('FilterBar').prop('onProcessScopeChange')('my-process');

  const loadTile = node.find('.AgenticControlPlane__kpi-section').find('DashboardRenderer').prop('loadTile');
  loadTile('report-id', [], {});

  expect(evaluateReport).toHaveBeenCalledWith('report-id', [], {}, [
    {key: 'my-process', versions: ['all']},
  ]);
});

it('should pass empty definitions in evaluate calls when no process is selected', async () => {
  const node = shallow(<AgenticControlPlane />);
  await runAllEffects();

  const loadTile = node.find('.AgenticControlPlane__kpi-section').find('DashboardRenderer').prop('loadTile');
  loadTile('report-id', [], {});

  expect(evaluateReport).toHaveBeenCalledWith('report-id', [], {}, []);
});
