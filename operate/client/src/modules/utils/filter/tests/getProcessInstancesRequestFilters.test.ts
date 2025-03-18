/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getProcessInstancesRequestFilters} from '../index';

describe('getProcessInstancesRequestFilters', () => {
  const originalLocation = window.location;

  afterEach(() => {
    Object.defineProperty(window, 'location', {
      value: originalLocation,
    });
  });

  it('should get process instances request filters', () => {
    const mockLocation = {
      search:
        '?active=true&incidents=true&completed=true&canceled=true&operationId=92449c1a-9d7a-4743-aaa3-f0661ead1bce&ids=9007199254741677&parentInstanceId=9007199254741678&startDateAfter=2023-09-05T00%3A00%3A00.000%2B0200&startDateBefore=2023-09-22T23%3A59%3A59.000%2B0200&endDateAfter=2023-09-01T00%3A00%3A00.000%2B0200&endDateBefore=2023-09-02T23%3A59%3A59.000%2B0200&errorMessage=test&process=bigVarProcess&version=1&flowNodeId=ServiceTask_0kt6c5i',
    };

    Object.defineProperty(window, 'location', {
      writable: true,
      value: mockLocation,
    });

    expect(getProcessInstancesRequestFilters()).toEqual({
      active: true,
      activityId: 'ServiceTask_0kt6c5i',
      batchOperationId: '92449c1a-9d7a-4743-aaa3-f0661ead1bce',
      canceled: true,
      completed: true,
      endDateAfter: '2023-09-01T00:00:00.000+0200',
      endDateBefore: '2023-09-02T23:59:59.000+0200',
      errorMessage: 'test',
      finished: true,
      ids: ['9007199254741677'],
      incidents: true,
      parentInstanceId: '9007199254741678',
      running: true,
      startDateAfter: '2023-09-05T00:00:00.000+0200',
      startDateBefore: '2023-09-22T23:59:59.000+0200',
    });
  });

  it('should not include tenant in request filters if value is all', () => {
    const mockLocation = {
      search: '?active=true&incidents=true&tenant=all',
    };

    Object.defineProperty(window, 'location', {
      writable: true,
      value: mockLocation,
    });

    expect(getProcessInstancesRequestFilters()).toEqual({
      active: true,
      incidents: true,
      running: true,
    });
  });

  it('should include tenant in request filters', () => {
    const mockLocation = {
      search: '?active=true&incidents=true&tenant=tenant-A',
    };

    Object.defineProperty(window, 'location', {
      writable: true,
      value: mockLocation,
    });

    expect(getProcessInstancesRequestFilters()).toEqual({
      active: true,
      incidents: true,
      running: true,
      tenantId: 'tenant-A',
    });
  });
});
