/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {waitFor} from 'modules/testing-library';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {decisionInstanceDetailsStore} from './decisionInstanceDetails';
import {mockFetchDecisionDefinitionXML} from 'modules/mocks/api/v2/decisionDefinitions/fetchDecisionDefinitionXML';
import {mockFetchDecisionInstance} from 'modules/mocks/api/decisionInstances/fetchDecisionInstance';

describe('decisionInstanceDetailsStore', () => {
  it('should initialize and reset ', async () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    mockFetchDecisionDefinitionXML().withSuccess(mockDmnXml);

    expect(decisionInstanceDetailsStore.state.status).toBe('initial');

    decisionInstanceDetailsStore.fetchDecisionInstance('22517947328274621');

    await waitFor(() =>
      expect(decisionInstanceDetailsStore.state.status).toBe('fetched'),
    );
    expect(decisionInstanceDetailsStore.state.decisionInstance).toEqual(
      invoiceClassification,
    );

    decisionInstanceDetailsStore.reset();

    expect(decisionInstanceDetailsStore.state.status).toBe('initial');
    expect(decisionInstanceDetailsStore.state.decisionInstance).toEqual(null);
  });
});
