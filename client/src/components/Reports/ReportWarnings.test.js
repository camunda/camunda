/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {incompatibleFilters} from 'services';

import ReportWarnings from './ReportWarnings';

jest.mock('services', () => {
  const rest = jest.requireActual('services');
  return {
    ...rest,
    incompatibleFilters: jest.fn(),
  };
});

const report = {
  id: '1',
  name: 'name',
  lastModifier: 'lastModifier',
  lastModified: '2017-11-11T11:11:11.1111+0200',
  reportType: 'process',
  combined: false,
  data: {
    processDefinitionKey: 'aKey',
    processDefinitionVersions: ['aVersion'],
    tenantIds: [],
    configuration: {},
    view: {proeprty: 'rawData', entity: null},
    groupBy: {type: 'none', value: null},
    visualization: 'table',
  },
  result: {data: [1, 2, 3], instanceCount: 37},
};

it('should show a warning message when there are incompatible filter ', async () => {
  incompatibleFilters.mockReturnValueOnce(true);
  const node = shallow(
    <ReportWarnings
      report={{
        ...report,
        data: {
          visualization: 'table',
          groupBy: {},
          view: {
            properties: ['rawData'],
          },
          filter: ['some data'],
        },
      }}
    />
  );

  expect(node.find('MessageBox').children()).toIncludeText(
    'No data shown due to incompatible filters'
  );
});

it('should show a warning when running node status filter is added on a grouped by endDate user task report', async () => {
  const node = shallow(
    <ReportWarnings
      report={{
        ...report,
        data: {
          visualization: 'table',
          view: {
            entity: 'userTask',
            properties: ['count'],
          },
          groupBy: {
            type: 'endDate',
          },
          filter: [{type: 'runningFlowNodesOnly', data: null, filterLevel: 'view'}],
        },
      }}
    />
  );

  expect(node.find('MessageBox').children()).toIncludeText(
    "Only completed flow nodes are considered when grouping by End Date. Therefore, adding 'running' flow node status filter will show no results"
  );
});
