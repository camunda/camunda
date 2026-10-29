/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {runAllEffects} from '__mocks__/react';
import {shallow} from 'enzyme';

import {MetricTable} from './MetricTable';
import {ProcessFailures} from './ProcessFailures';
import {loadProcessFailures} from './service';

jest.mock('hooks', () => ({
  useErrorHandling: jest.fn(() => ({
    mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  })),
}));

jest.mock('notifications', () => ({showError: jest.fn()}));

jest.mock('./service', () => ({loadProcessFailures: jest.fn()}));

const agents = [
  {
    agentId: 'loan:review',
    name: 'Review agent',
    runs: 100,
    failedRuns: 6,
    successRate: 94,
    topReasons: [],
  },
];

it('should load the failing agents of the process and open an agent', async () => {
  (loadProcessFailures as jest.Mock).mockReturnValue(agents);
  const onSelectAgent = jest.fn();
  const endedFrom = new Date('2026-10-01T00:00:00.000Z');
  const node = shallow(
    <ProcessFailures
      processDefinitionKey="loan"
      endedFrom={endedFrom}
      onSelectAgent={onSelectAgent}
    />
  );
  await runAllEffects();

  expect(loadProcessFailures).toHaveBeenCalledWith('loan', endedFrom);
  const metricTable = node.find(MetricTable);
  expect(metricTable.prop('rows')).toEqual(agents);

  const columns = metricTable.prop('columns') as {render: (row: unknown) => JSX.Element}[];
  const agentCell = shallow(<div>{columns[0]!.render(agents[0])}</div>);
  agentCell.find('button').simulate('click');

  expect(onSelectAgent).toHaveBeenCalledWith('loan:review');
});
