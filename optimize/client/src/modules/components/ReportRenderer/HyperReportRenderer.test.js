/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import HyperReportRenderer from './HyperReportRenderer';

it('should convert a hypermap to a combined report', () => {
  const node = shallow(
    <HyperReportRenderer
      report={{
        combined: false,
        reportType: 'process',
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
      }}
    />
  );

  expect(node).toMatchSnapshot();
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

  expect(node).toMatchSnapshot();
});

it('should format single reports names for hyper reports distributed by date variable', () => {
  const node = shallow(
    <HyperReportRenderer
      report={{
        combined: false,
        reportType: 'process',
        data: {
          processDefinitionKey: 'aKey',
          processDefinitionVersion: '1',
          view: {
            properties: ['frequency'],
            entity: 'processInstance',
          },
          groupBy: {
            type: 'startDate',
          },
          visualization: 'bar',
          distributedBy: {
            type: 'variable',
            value: {type: 'Date'},
          },
          configuration: {
            distributeByDateVariableUnit: 'month',
          },
        },
        result: {
          measures: [
            {
              property: 'frequency',
              data: [
                {
                  key: '2020-09-01T00:00:00.000+0200',
                  label: '2020-09-01T00:00:00.000+0200',
                  value: [
                    {
                      key: '1969-12-07T14:52:00Z',
                      value: 1.0,
                      label: '1969-12-07T14:52:00Z',
                    },
                    {
                      key: '2020-03-08T14:52:00Z',
                      value: 1.0,
                      label: '2020-03-08T14:52:00Z',
                    },
                  ],
                },
              ],
            },
          ],
          instanceCount: 1234,
          type: 'hyperMap',
        },
      }}
    />
  );

  const names = Object.values(node.prop('report').result.data).map(({name}) => name);

  expect(names).toEqual(['Dec 1969', 'Mar 2020']);
});
