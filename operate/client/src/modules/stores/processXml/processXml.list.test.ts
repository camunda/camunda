/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {waitFor} from '@testing-library/react';
import {mockProcessXml} from 'modules/mocks/mockProcessXml';
import {processXmlStore} from './processXml.list';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';

describe('stores/processXml/processXml.list', () => {
  afterEach(() => {
    processXmlStore.reset();
  });

  it('should get flowNodeFilterOptions', async () => {
    mockFetchProcessXML().withSuccess(mockProcessXml);

    processXmlStore.fetchProcessXml('1');

    await waitFor(() => expect(processXmlStore.state.status).toBe('fetched'));

    expect(processXmlStore.flowNodeFilterOptions).toEqual([
      {label: 'EndEvent_0crvjrk', value: 'EndEvent_0crvjrk'},
      {label: 'StartEvent_1', value: 'StartEvent_1'},
      {label: 'userTask', value: 'userTask'},
    ]);
  });
});
