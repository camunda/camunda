/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {waitFor} from 'modules/testing-library';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {mockDrdData} from 'modules/mocks/mockDrdData';
import {decisionInstanceDetailsStore} from './decisionInstanceDetails';
import {drdDataStore} from './drdData';
import {mockFetchDrdData} from 'modules/mocks/api/decisionInstances/fetchDrdData';
import {mockFetchDecisionInstance} from 'modules/mocks/api/decisionInstances/fetchDecisionInstance';

describe('drdDataStore', () => {
  afterEach(() => {
    drdDataStore.reset();
    decisionInstanceDetailsStore.reset();
  });

  it('should fetch DRD data', async () => {
    mockFetchDrdData().withSuccess(mockDrdData);

    drdDataStore.fetchDrdData('1');
    await waitFor(() => expect(drdDataStore.state.status).toBe('fetched'));
    expect(drdDataStore.state.drdData).toEqual(mockDrdData);
  });

  it('should catch error', async () => {
    mockFetchDrdData().withServerError();
    drdDataStore.fetchDrdData('1');
    await waitFor(() => expect(drdDataStore.state.status).toBe('error'));
    expect(drdDataStore.state.drdData).toEqual(null);
  });

  it('should get current decision', async () => {
    mockFetchDrdData().withSuccess(mockDrdData);
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    decisionInstanceDetailsStore.fetchDecisionInstance('1');
    drdDataStore.fetchDrdData('1');

    await waitFor(() => expect(drdDataStore.state.status).toBe('fetched'));

    expect(drdDataStore.currentDecision).toEqual('invoiceClassification');
  });
});
