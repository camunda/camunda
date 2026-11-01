/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {shallow} from 'enzyme';

import {AgentView} from './AgentView';
import {MetricTable} from './MetricTable';
import type {AgentDetail} from './service';

const agent: AgentDetail = {
  agentId: 'loan:review',
  name: 'Review agent',
  agentType: 'AI_AGENT_SUB_PROCESS',
  processDefinitionKey: 'loan',
  processDefinitionName: 'Loan approval',
  processDefinitionVersion: '7',
  model: 'claude-sonnet',
  provider: 'anthropic',
  maxModelCalls: 10,
  maxToolCalls: null,
  maxTokens: 8000,
  runs: 100,
  failedRuns: 6,
  avgModelCalls: 4.2,
  limitHits: [{limit: 'maxModelCalls', runs: 4}],
  failureReasons: [
    {
      kind: 'LIMIT',
      limit: 'maxModelCalls',
      flowNodeId: null,
      toolName: null,
      errorType: null,
      message: null,
      runs: 4,
    },
    {
      kind: 'INCIDENT',
      limit: null,
      flowNodeId: 'lookup',
      toolName: 'Look up merchant',
      errorType: 'JOB_NO_RETRIES',
      message: 'HTTP 503',
      runs: 2,
    },
  ],
  tools: [
    {
      toolFlowNodeId: 'lookup',
      toolName: 'Look up merchant',
      calls: 180,
      callsPerRun: 1.8,
      incidents: 2,
      failureRate: 1.1,
    },
  ],
  toolsAvailable: true,
  models: [
    {
      model: 'claude-sonnet',
      provider: 'anthropic',
      runs: 100,
      avgTokens: 5000,
      avgDurationMs: 40000,
      successRate: 94,
    },
  ],
  whereUsed: [
    {rootProcessDefinitionKey: 'loan', rootProcessName: 'Loan approval', runs: 75, totalTokens: 1},
    {rootProcessDefinitionKey: 'chat', rootProcessName: null, runs: 25, totalTokens: 1},
  ],
};

function table(node: ReturnType<typeof shallow>, className: string) {
  return node.find(MetricTable).filterWhere((n) => n.prop('className') === className);
}

function cells(node: ReturnType<typeof shallow>, className: string, rowIndex: number) {
  const {columns, rows} = table(node, className).props() as {
    columns: {render: (row: unknown) => unknown}[];
    rows: unknown[];
  };
  return columns.map(({render}) => render(rows[rowIndex]));
}

it('should show the agent header with model, limits and version', () => {
  const node = shallow(<AgentView agent={agent} />);
  const header = node.find('AgentHeader').dive();

  expect(header.find('.agentName').text()).toBe('Review agent');
  const text = header.find('Meta').map((meta) => String(meta.prop('children')));
  expect(text).toContain('claude-sonnet (anthropic)');
  expect(text).toContain('7');
  expect(text).toContain('AI Agent Sub-process');
  expect(text).toContain('10 max model calls · 8,000 max tokens');
  expect(text).toContain('4.2 of 10');
});

it('should render the slot for the agent tiles', () => {
  const node = shallow(
    <AgentView agent={agent}>
      <div className="tiles" />
    </AgentView>
  );

  expect(node.find('.tiles')).toExist();
});

it('should list failure reasons in the ranked order with kind, element and message', () => {
  const node = shallow(<AgentView agent={agent} />);

  expect(table(node, 'failureReasons').prop('rows')).toEqual(agent.failureReasons);
  const [, limitElement] = cells(node, 'failureReasons', 0);
  expect(limitElement).toBe('Limit hit: max model calls');
  const [, toolElement, errorType, message, runs] = cells(node, 'failureReasons', 1);
  expect(toolElement).toBe('Tool: Look up merchant');
  expect(errorType).toBe('JOB_NO_RETRIES');
  expect(message).toBe('HTTP 503');
  expect(runs).toBe('2');
});

it('should show the tools, model comparison and where used tables', () => {
  const node = shallow(<AgentView agent={agent} />);

  expect(table(node, 'tools').prop('rows')).toEqual(agent.tools);
  expect(cells(node, 'tools', 0)).toEqual(['Look up merchant', '180', '1.8', '1.1%']);
  expect(table(node, 'models').prop('rows')).toEqual(agent.models);
  expect(cells(node, 'whereUsed', 0)).toEqual(['Loan approval', '75', '1', '75%']);
  expect(cells(node, 'whereUsed', 1)[0]).toBe('chat');
  expect(node.find('.toolsUnavailable')).not.toExist();
});

it('should explain that per-tool data only exists for AI Agent Sub-process agents', () => {
  const node = shallow(
    <AgentView agent={{...agent, agentType: 'AI_AGENT_TASK', tools: [], toolsAvailable: false}} />
  );

  expect(node.find('.toolsUnavailable')).toExist();
  expect(table(node, 'tools')).not.toExist();
});
