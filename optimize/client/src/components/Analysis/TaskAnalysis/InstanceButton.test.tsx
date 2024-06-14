/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {shallow} from 'enzyme';

import {loadRawData} from 'services';
import {DownloadButton} from 'components';

import {InstancesButton} from './InstanceButton';

jest.mock('services', () => ({
  ...jest.requireActual('services'),
  formatters: {
    formatFileName: (name: string) => name,
  },
  loadRawData: jest.fn(),
}));

const props = {
  id: 'flowNodeId',
  name: 'flowNodeName',
  value: 1250,
  config: {
    processDefinitionKey: 'defKey',
    processDefinitionVersions: ['1'],
    tenantIds: ['sales'],
    filters: [
      {
        type: 'filter',
        data: {},
        filterLevel: 'instance',
      },
    ],
  },
  totalCount: 0,
};

it('invoke loadRawData on button Click', async () => {
  const node = shallow(<InstancesButton {...props} />);

  await node.find(DownloadButton).prop('retriever')?.();

  expect(loadRawData).toHaveBeenCalledWith({
    filter: [
      {
        type: 'completedInstancesOnly',
        filterLevel: 'instance',
      },
      {
        data: {flowNodeId: {operator: '>=', unit: 'millis', value: 1250}},
        type: 'flowNodeDuration',
        filterLevel: 'instance',
      },
      {
        type: 'filter',
        data: {},
        filterLevel: 'instance',
      },
    ],
    includedColumns: ['processInstanceId'],
    processDefinitionKey: 'defKey',
    processDefinitionVersions: ['1'],
    tenantIds: ['sales'],
  });
});
