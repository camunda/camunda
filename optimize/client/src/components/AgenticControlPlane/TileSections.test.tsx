/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {evaluateReport} from 'services';

import {TileSections} from './TileSections';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  evaluateReport: jest.fn(),
}));

const tiles = [
  {id: 'kpi-all', configuration: {levels: ['overview', 'process', 'agent'], section: 'kpi'}},
  {id: 'trend', configuration: {levels: ['overview', 'process'], section: 'trends', topN: '5'}},
  {id: 'agent-trend', configuration: {levels: ['agent'], section: 'agentTrends'}},
  {id: 'no-config'},
];

type LoadTile = (id: string, filter: unknown[], query: Record<string, unknown>) => void;

function render(props: Partial<Parameters<typeof TileSections>[0]> = {}) {
  return shallow(
    <TileSections
      tiles={tiles as never}
      level="overview"
      sections={['kpi', 'trends']}
      definitions={[{key: 'loan', versions: ['3']}]}
      filter={[]}
      groupByDateUnit="week"
      scopeKey="scope"
      {...props}
    />
  );
}

function rendererOf(node: ReturnType<typeof shallow>, section: string) {
  return node.find(`.${section}-section`).find('DashboardRenderer');
}

function tileIds(node: ReturnType<typeof shallow>, section: string) {
  return (rendererOf(node, section).prop('tiles') as {id: string}[]).map(({id}) => id);
}

beforeEach(() => jest.clearAllMocks());

it('should place tiles by level and section', () => {
  const overview = render();
  expect(tileIds(overview, 'kpi')).toEqual(['kpi-all', 'no-config']);
  expect(tileIds(overview, 'trends')).toEqual(['trend']);

  const agent = render({level: 'agent', sections: ['kpi', 'agentTrends']});
  expect(tileIds(agent, 'kpi')).toEqual(['kpi-all']);
  expect(tileIds(agent, 'agentTrends')).toEqual(['agent-trend']);
  expect(agent.find('.trends-section')).not.toExist();
});

it('should evaluate KPI tiles with the definitions only', () => {
  const node = render();

  (rendererOf(node, 'kpi').prop('loadTile') as LoadTile)('kpi-all', [], {});

  expect(evaluateReport).toHaveBeenCalledWith('kpi-all', [], {}, [{key: 'loan', versions: ['3']}]);
});

it('should evaluate trend tiles with the date unit and their top-N limit', () => {
  const node = render();

  (rendererOf(node, 'trends').prop('loadTile') as LoadTile)('trend', [], {});

  expect(evaluateReport).toHaveBeenCalledWith(
    'trend',
    [],
    {limit: 5},
    [{key: 'loan', versions: ['3']}],
    'week'
  );
});

it('should pass the filter to every renderer', () => {
  const filter = [{type: 'agentRun'}];
  const node = render({filter});

  expect(rendererOf(node, 'kpi').prop('filter')).toBe(filter);
  expect(rendererOf(node, 'trends').prop('filter')).toBe(filter);
});
