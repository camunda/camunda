/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {parseProcessInstancesSearchFilter} from './processInstancesSearch';

const params = (obj: Record<string, string>) => new URLSearchParams(obj);

describe('parseProcessInstancesSearchFilter', () => {
  it('should return undefined when no filters are set', () => {
    expect(
      parseProcessInstancesSearchFilter(new URLSearchParams()),
    ).toBeUndefined();
  });

  it('should return a filter with elementId and elementInstanceState when only elementId is set', () => {
    const result = parseProcessInstancesSearchFilter(
      params({elementId: 'nodeA'}),
    );

    expect(result).toEqual({
      elementId: {$eq: 'nodeA'},
      elementInstanceState: {$eq: 'ACTIVE'},
    });
  });

  it('should return a filter with state when only active is set', () => {
    const result = parseProcessInstancesSearchFilter(params({active: 'true'}));

    expect(result).toEqual({
      state: {$eq: 'ACTIVE'},
      hasIncident: false,
    });
  });

  it('should return a filter combining state and elementId when both are set', () => {
    const result = parseProcessInstancesSearchFilter(
      params({active: 'true', elementId: 'nodeA'}),
    );

    expect(result).toMatchObject({
      state: {$eq: 'ACTIVE'},
      hasIncident: false,
      elementId: {$eq: 'nodeA'},
      elementInstanceState: {$eq: 'ACTIVE'},
    });
  });

  it('should return a filter with batchOperationKey when only batchOperationKey is set', () => {
    const result = parseProcessInstancesSearchFilter(
      params({batchOperationKey: 'batch-123'}),
    );

    expect(result).toEqual({
      batchOperationKey: {$eq: 'batch-123'},
    });
  });

  it('should map businessId filter to an advanced string filter', () => {
    const result = parseProcessInstancesSearchFilter(
      params({active: 'true', businessId: 'eq_order-123___like_order'}),
    );
    expect(result).toMatchObject({
      businessId: {$eq: 'order-123', $like: '*order*'},
    });
  });

  it('omits businessId when the value is malformed', () => {
    const result = parseProcessInstancesSearchFilter(
      params({active: 'true', businessId: 'legacy-bare-value'}),
    );
    expect(result).not.toHaveProperty('businessId');
  });

  it('should return a filter with the SUSPENDED state when only suspended is set', () => {
    const result = parseProcessInstancesSearchFilter(
      params({suspended: 'true'}),
    );

    expect(result).toEqual({
      state: {$eq: 'SUSPENDED'},
    });
  });

  it('should combine active and suspended into an $or clause', () => {
    const result = parseProcessInstancesSearchFilter(
      params({active: 'true', suspended: 'true'}),
    );

    expect(result).toEqual({
      $or: [
        {state: {$eq: 'ACTIVE'}, hasIncident: false},
        {state: {$eq: 'SUSPENDED'}},
      ],
    });
  });

  it('should combine active, suspended and incidents into a three-clause $or', () => {
    const result = parseProcessInstancesSearchFilter(
      params({active: 'true', suspended: 'true', incidents: 'true'}),
    );

    expect(result).toEqual({
      $or: [
        {state: {$eq: 'ACTIVE'}, hasIncident: false},
        {state: {$eq: 'SUSPENDED'}},
        {hasIncident: true, state: {$neq: 'SUSPENDED'}},
      ],
    });
  });

  it('should exclude suspended instances when only incidents is set', () => {
    const result = parseProcessInstancesSearchFilter(
      params({incidents: 'true'}),
    );

    expect(result).toEqual({
      hasIncident: true,
      state: {$neq: 'SUSPENDED'},
    });
  });

  it('should combine suspended with other finished states into an $or clause', () => {
    const result = parseProcessInstancesSearchFilter(
      params({suspended: 'true', completed: 'true', canceled: 'true'}),
    );

    expect(result).toEqual({
      $or: [
        {state: {$in: ['COMPLETED', 'TERMINATED']}, hasIncident: false},
        {state: {$eq: 'SUSPENDED'}},
      ],
    });
  });
});
