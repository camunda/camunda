/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {waitFor} from '@testing-library/react';
import {processXmlStore} from './processXml.migration.source';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {open} from 'modules/mocks/diagrams';

describe('stores/processXml/processXml.list', () => {
  afterEach(() => {
    processXmlStore.reset();
  });

  it('should filter selectable flow nodes', async () => {
    mockFetchProcessXML().withSuccess(open('orderProcess.bpmn'));

    processXmlStore.fetchProcessXml('1');
    expect(processXmlStore.state.status).toBe('fetching');
    await waitFor(() => expect(processXmlStore.state.status).toBe('fetched'));

    expect(
      processXmlStore.selectableFlowNodes.map((flowNode) => flowNode.id),
    ).toEqual(['checkPayment', 'shipArticles', 'requestForPayment']);
  });
});
