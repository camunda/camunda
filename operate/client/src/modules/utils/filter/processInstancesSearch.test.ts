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
    expect(parseProcessInstancesSearchFilter(new URLSearchParams())).toBeUndefined();
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
});
