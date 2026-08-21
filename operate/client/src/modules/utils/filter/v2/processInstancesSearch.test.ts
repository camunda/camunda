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
  it('should preserve active element semantics when active is selected', () => {
    const result = parseProcessInstancesSearchFilter(
      params({active: 'true', elementId: 'nodeA'}),
    );

    expect(result).toEqual({
      state: {$eq: 'ACTIVE'},
      hasIncident: false,
      elementId: {$eq: 'nodeA'},
      elementInstanceState: {$eq: 'ACTIVE'},
    });
  });

  it('should not force elementInstanceState to ACTIVE when completed is selected', () => {
    const result = parseProcessInstancesSearchFilter(
      params({completed: 'true', elementId: 'nodeA'}),
    );

    expect(result).toEqual({
      state: {$eq: 'COMPLETED'},
      hasIncident: false,
      elementId: {$eq: 'nodeA'},
    });
  });

  it('should not force elementInstanceState to ACTIVE when canceled is selected', () => {
    const result = parseProcessInstancesSearchFilter(
      params({canceled: 'true', elementId: 'nodeA'}),
    );

    expect(result).toEqual({
      state: {$eq: 'TERMINATED'},
      hasIncident: false,
      elementId: {$eq: 'nodeA'},
    });
  });

  it('should match active and executed elements in separate branches', () => {
    const result = parseProcessInstancesSearchFilter(
      params({
        active: 'true',
        completed: 'true',
        elementId: 'nodeA',
      }),
    );

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

  it('should match incident and executed elements in separate branches', () => {
    const result = parseProcessInstancesSearchFilter(
      params({
        incidents: 'true',
        canceled: 'true',
        elementId: 'nodeA',
      }),
    );

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
});
