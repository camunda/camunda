/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {waitFor} from 'modules/testing-library';
import {groupedDecisionsStore} from '../';
import {mockFetchGroupedDecisions} from 'modules/mocks/api/decisions/fetchGroupedDecisions';

describe('fetch error', () => {
  beforeEach(async () => {
    mockFetchGroupedDecisions().withServerError();

    groupedDecisionsStore.fetchDecisions();

    await waitFor(() => {
      expect(groupedDecisionsStore.state.status === 'error').toBe(true);
    });
  });

  afterEach(() => {
    groupedDecisionsStore.reset();
  });

  it('should keep decisions empty', () => {
    expect(groupedDecisionsStore.state.decisions).toEqual([]);
    expect(groupedDecisionsStore.areDecisionsEmpty).toBe(true);
  });

  it('should return null', () => {
    expect(
      groupedDecisionsStore.getDecisionDefinitionId({
        decisionId: 'invoice-assign-approver',
        version: 1,
      }),
    ).toBe(null);
  });
});
