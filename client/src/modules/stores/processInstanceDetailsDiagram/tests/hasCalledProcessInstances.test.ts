/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {processInstanceDetailsDiagramStore} from '../../processInstanceDetailsDiagram';
import {processInstanceDetailsStore} from '../../processInstanceDetails';
import {
  createInstance,
  mockProcessXML,
  mockCallActivityProcessXML,
} from 'modules/testUtils';
import {waitFor} from 'modules/testing-library';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';

describe('hasCalledProcessInstances', () => {
  afterEach(() => {
    processInstanceDetailsDiagramStore.reset();
    processInstanceDetailsStore.reset();
  });

  it('should return true for processes with call activity', async () => {
    mockFetchProcessXML().withSuccess(mockCallActivityProcessXML);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
        processId: '10',
      })
    );

    processInstanceDetailsDiagramStore.init();

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toEqual('fetched')
    );

    expect(processInstanceDetailsDiagramStore.hasCalledProcessInstances).toBe(
      true
    );
  });

  it('should return false for processes without call activity', async () => {
    mockFetchProcessXML().withSuccess(mockProcessXML);

    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: '123',
        state: 'ACTIVE',
        processId: '10',
      })
    );

    processInstanceDetailsDiagramStore.init();

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toEqual('fetched')
    );

    expect(processInstanceDetailsDiagramStore.hasCalledProcessInstances).toBe(
      false
    );
  });
});
