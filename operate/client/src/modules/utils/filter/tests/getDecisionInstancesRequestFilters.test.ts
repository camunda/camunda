/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getDecisionInstancesRequestFilters} from '../index';

describe('getDecisionInstancesRequestFilters', () => {
  const originalLocation = window.location;

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      value: originalLocation,
    });
  });
  it('should get decision instances request filters', () => {
    const mockLocation = {
      search:
        '?evaluated=true&failed=true&decisionInstanceIds=2251799813690843-1&evaluationDateAfter=2023-08-29T00%3A00%3A00.000%2B0200&evaluationDateBefore=2023-09-28T23%3A59%3A59.000%2B0200&processInstanceId=2251799813690838&name=invoiceAssignApprover&version=2',
    };

    Object.defineProperty(window, 'location', {
      writable: true,
      value: mockLocation,
    });

    expect(getDecisionInstancesRequestFilters()).toEqual({
      evaluated: true,
      evaluationDateAfter: '2023-08-29T00:00:00.000+0200',
      evaluationDateBefore: '2023-09-28T23:59:59.000+0200',
      failed: true,
      ids: ['2251799813690843-1'],
      processInstanceId: '2251799813690838',
    });
  });

  it('should not include tenant in request filters if value is all', () => {
    const mockLocation = {
      search: '?evaluated=true&failed=true&tenant=all',
    };

    Object.defineProperty(window, 'location', {
      writable: true,
      value: mockLocation,
    });

    expect(getDecisionInstancesRequestFilters()).toEqual({
      evaluated: true,
      failed: true,
    });
  });

  it('should include tenant in request filters', () => {
    const mockLocation = {
      search: '?evaluated=true&failed=true&tenant=tenant-A',
    };

    Object.defineProperty(window, 'location', {
      writable: true,
      value: mockLocation,
    });

    expect(getDecisionInstancesRequestFilters()).toEqual({
      evaluated: true,
      failed: true,
      tenantId: 'tenant-A',
    });
  });
});
