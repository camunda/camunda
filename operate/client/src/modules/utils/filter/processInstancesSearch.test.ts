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
      parseProcessInstancesSearchFilter({
        searchParams: new URLSearchParams(),
      }),
    ).toBeUndefined();
  });

  it('should return a filter with elementId and elementInstanceState when only elementId is set', () => {
    const result = parseProcessInstancesSearchFilter({
      searchParams: params({elementId: 'nodeA'}),
    });

    expect(result).toEqual({
      elementId: {$eq: 'nodeA'},
      elementInstanceState: {$eq: 'ACTIVE'},
    });
  });

  it('should return a filter with state when only active is set', () => {
    const result = parseProcessInstancesSearchFilter({
      searchParams: params({active: 'true'}),
    });

    expect(result).toEqual({
      state: {$eq: 'ACTIVE'},
      hasIncident: false,
    });
  });

  it('should return a filter combining state and elementId when both are set', () => {
    const result = parseProcessInstancesSearchFilter({
      searchParams: params({active: 'true', elementId: 'nodeA'}),
    });

    expect(result).toEqual({
      state: {$eq: 'ACTIVE'},
      hasIncident: false,
      elementId: {$eq: 'nodeA'},
      elementInstanceState: {$eq: 'ACTIVE'},
    });
  });

  it('should not force elementInstanceState to ACTIVE when completed is set alongside elementId', () => {
    const result = parseProcessInstancesSearchFilter({
      searchParams: params({completed: 'true', elementId: 'nodeA'}),
    });

    expect(result).toEqual({
      state: {$eq: 'COMPLETED'},
      hasIncident: false,
      elementId: {$eq: 'nodeA'},
    });
  });

  it('should not force elementInstanceState to ACTIVE when canceled is set alongside elementId', () => {
    const result = parseProcessInstancesSearchFilter({
      searchParams: params({canceled: 'true', elementId: 'nodeA'}),
    });

    expect(result).toEqual({
      state: {$eq: 'TERMINATED'},
      hasIncident: false,
      elementId: {$eq: 'nodeA'},
    });
  });

  it('should match active elements and executed elements in separate branches', () => {
    const result = parseProcessInstancesSearchFilter({
      searchParams: params({
        active: 'true',
        completed: 'true',
        elementId: 'nodeA',
      }),
    });

    expect(result).toEqual({
      $or: [
        {
          elementId: {$eq: 'nodeA'},
          elementInstanceState: {$eq: 'ACTIVE'},
          state: {$eq: 'ACTIVE'},
          hasIncident: false,
        },
        {
          elementId: {$eq: 'nodeA'},
          state: {$eq: 'COMPLETED'},
          hasIncident: false,
        },
      ],
    });
  });

  it('should match incident elements and executed elements in separate branches', () => {
    const result = parseProcessInstancesSearchFilter({
      searchParams: params({
        incidents: 'true',
        canceled: 'true',
        elementId: 'nodeA',
      }),
    });

    expect(result).toEqual({
      $or: [
        {
          elementId: {$eq: 'nodeA'},
          state: {$eq: 'TERMINATED'},
          hasIncident: false,
        },
        {
          elementId: {$eq: 'nodeA'},
          elementInstanceState: {$eq: 'ACTIVE'},
          hasIncident: true,
        },
      ],
    });
  });

  it('should match suspended elements and executed elements in separate branches', () => {
    const result = parseProcessInstancesSearchFilter({
      searchParams: params({
        suspended: 'true',
        completed: 'true',
        elementId: 'nodeA',
      }),
      includeSuspended: true,
    });

    expect(result).toEqual({
      $or: [
        {
          elementId: {$eq: 'nodeA'},
          state: {$eq: 'COMPLETED'},
          hasIncident: false,
        },
        {
          elementId: {$eq: 'nodeA'},
          elementInstanceState: {$eq: 'ACTIVE'},
          state: {$eq: 'SUSPENDED'},
        },
      ],
    });
  });

  it('should return a filter with batchOperationKey when only batchOperationKey is set', () => {
    const result = parseProcessInstancesSearchFilter({
      searchParams: params({batchOperationKey: 'batch-123'}),
    });

    expect(result).toEqual({
      batchOperationKey: {$eq: 'batch-123'},
    });
  });

  it('should map businessId filter to an advanced string filter', () => {
    const result = parseProcessInstancesSearchFilter({
      searchParams: params({
        active: 'true',
        businessId: 'eq_order-123___like_order',
      }),
    });
    expect(result).toMatchObject({
      businessId: {$eq: 'order-123', $like: '*order*'},
    });
  });

  it('omits businessId when the value is malformed', () => {
    const result = parseProcessInstancesSearchFilter({
      searchParams: params({
        active: 'true',
        businessId: 'legacy-bare-value',
      }),
    });
    expect(result).not.toHaveProperty('businessId');
  });
});
