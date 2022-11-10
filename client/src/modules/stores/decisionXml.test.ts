/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {waitFor} from 'modules/testing-library';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {decisionXmlStore} from './decisionXml';
import {decisionInstanceDetailsStore} from './decisionInstanceDetails';
import {mockFetchDecisionXML} from 'modules/mocks/api/decisions/fetchDecisionXML';
import {mockFetchDecisionInstance} from 'modules/mocks/api/decisionInstances/fetchDecisionInstance';

describe('decisionXmlStore', () => {
  it('should initialize and reset ', async () => {
    mockFetchDecisionXML().withSuccess(mockDmnXml);
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    expect(decisionXmlStore.state.status).toBe('initial');

    decisionXmlStore.init();
    decisionInstanceDetailsStore.fetchDecisionInstance('4423094875234230');

    await waitFor(() => expect(decisionXmlStore.state.status).toBe('fetched'));
    expect(decisionXmlStore.state.xml).toEqual(mockDmnXml);

    decisionXmlStore.reset();
    decisionInstanceDetailsStore.reset();

    expect(decisionXmlStore.state.status).toBe('initial');
    expect(decisionXmlStore.state.xml).toEqual(null);
  });
});
