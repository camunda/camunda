/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {processResult} from 'services';

import {Chart} from './visualizations';
import HyperReportRenderer from './HyperReportRenderer';
import ProcessReportRenderer from './ProcessReportRenderer';

jest.mock('./service', () => {
  return {
    getFormatter: () => (v) => v,
  };
});

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    formatters: {formatReportResult: (_data, result) => result},
    processResult: jest.fn().mockImplementation(({result}) => result),
  };
});

const userTaskAssigneeReport = {
  data: {
    processDefinitionKey: 'aKey',
    processDefinitionVersion: '1',
    view: {
      properties: ['duration'],
      entity: 'userTask',
    },
    groupBy: {
      type: 'assignee',
    },
    visualization: 'bar',
    distributedBy: {type: 'userTask', value: null},
    configuration: {},
  },
  result: {
    measures: [
      {
        property: 'duration',
        data: [
          {
            key: 'Anne',
            label: 'Anne',
            value: [
              {key: 'taskId1', label: 'Usertask 1', value: 8},
              {key: 'taskId2', label: 'Usertask 2', value: 1},
              {key: 'taskId3', label: 'Usertask 3', value: 65},
            ],
          },
          {
            key: 'Bernd',
            label: 'Bernd',
            value: [
              {key: 'taskId1', label: 'Usertask 1', value: 3},
              {key: 'taskId2', label: 'Usertask 2', value: 17},
              {key: 'taskId3', label: 'Usertask 3', value: 22},
            ],
          },
          {
            key: 'Chris',
            label: 'Chris',
            value: [
              {key: 'taskId1', label: 'Usertask 1', value: 1},
              {key: 'taskId2', label: 'Usertask 2', value: 0},
              {key: 'taskId3', label: 'Usertask 3', value: 73},
            ],
          },
        ],
      },
    ],
    instanceCount: 1234,
    type: 'hyperMap',
  },
};

it('should convert a hypermap to a hyper report', () => {
  const node = shallow(<HyperReportRenderer report={userTaskAssigneeReport} />);

  expect(node.find(Chart).prop('report').hyper).toBe(true);
});

it('should render single process report if report result is empty', () => {
  const node = shallow(
    <HyperReportRenderer
      report={{
        result: {
          measures: [{data: []}],
          instanceCount: 0,
          type: 'hyperMap',
        },
      }}
    />
  );

  expect(node.find(ProcessReportRenderer)).toExist();
});

it('should convert and adjust the result to be identical to hyper report result structure', () => {
  const node = shallow(<HyperReportRenderer report={userTaskAssigneeReport} />);

  const resultData = node.find(Chart).prop('report').result.data;

  expect(resultData['taskId1'].result.data).toEqual(userTaskAssigneeReport.result.measures[0].data);
  expect(resultData['taskId1'].result.measures[0].data).toEqual([
    {key: 'Anne', label: 'Anne', value: 8},
    {key: 'Bernd', label: 'Bernd', value: 3},
    {key: 'Chris', label: 'Chris', value: 1},
  ]);
});

it('should process the result of every created report', () => {
  processResult.mockClear();

  shallow(<HyperReportRenderer report={userTaskAssigneeReport} />);

  expect(processResult).toHaveBeenCalledTimes(3);
  const reportNames = processResult.mock.calls.map((call) => call[0].id);
  expect(reportNames).toEqual(['taskId1', 'taskId2', 'taskId3']);
});
