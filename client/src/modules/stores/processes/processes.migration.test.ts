/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {groupedProcessesMock} from 'modules/testUtils';
import {processesStore} from './processes.migration';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {generateProcessKey} from 'modules/utils/generateProcessKey';

describe('processes.migration store', () => {
  afterEach(() => processesStore.reset);

  it('should get targetProcessVersions', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    await processesStore.fetchProcesses();

    expect(processesStore.targetProcessVersions).toEqual([]);

    processesStore.setSelectedTargetProcess(generateProcessKey('demoProcess'));

    expect(processesStore.targetProcessVersions).toEqual([1, 2, 3]);

    processesStore.setSelectedTargetProcess(
      generateProcessKey('bigVarProcess', '<tenant-A>'),
    );

    expect(processesStore.targetProcessVersions).toEqual([1, 2]);
  });

  it('should get selectedTargetProcessId', async () => {
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    await processesStore.fetchProcesses();

    expect(processesStore.selectedTargetProcessId).toBeUndefined();

    processesStore.setSelectedTargetProcess(generateProcessKey('demoProcess'));
    processesStore.setSelectedTargetVersion(2);

    expect(processesStore.selectedTargetProcessId).toEqual('demoProcess2');

    processesStore.setSelectedTargetProcess(
      generateProcessKey('bigVarProcess', '<tenant-A>'),
    );
    processesStore.setSelectedTargetVersion(1);

    expect(processesStore.selectedTargetProcessId).toEqual('2251799813685894');
  });
});
