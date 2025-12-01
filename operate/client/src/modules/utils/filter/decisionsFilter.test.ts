/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  parseDecisionDefinitionsSearchFilter,
  parseDecisionInstancesSearchFilter,
  parseDecisionInstancesSearchSort,
} from './decisionsFilter';

describe('parseDecisionInstancesSearchFilter', () => {
  it('should parse decision instances search filter from search params', () => {
    const searchParams = new URLSearchParams();
    searchParams.append('evaluated', 'true');
    searchParams.append('failed', 'true');
    searchParams.append('name', 'testName');
    searchParams.append('version', '3');
    searchParams.append('processInstanceId', '2251799813690838');
    searchParams.append('evaluationDateAfter', '2023-08-29T00:00:00.000Z');
    searchParams.append('evaluationDateBefore', '2023-09-28T23:59:59.000Z');
    searchParams.append('tenant', 'tenant-A');
    searchParams.append(
      'decisionInstanceIds',
      '2251799813702856-1 2251799813702857-1',
    );

    const filter = parseDecisionInstancesSearchFilter(searchParams);

    expect(filter).toEqual({
      decisionEvaluationInstanceKey: {
        $in: ['2251799813702856-1', '2251799813702857-1'],
      },
      state: {$in: ['EVALUATED', 'FAILED']},
      decisionDefinitionId: 'testName',
      decisionDefinitionVersion: 3,
      processInstanceKey: '2251799813690838',
      tenantId: 'tenant-A',
      evaluationDate: {
        $gt: '2023-08-29T00:00:00.000Z',
        $lt: '2023-09-28T23:59:59.000Z',
      },
    });
  });

  it('should return undefined when no state param is set', () => {
    const searchParams = new URLSearchParams();
    searchParams.append('name', 'testName');

    const filter = parseDecisionInstancesSearchFilter(searchParams);

    expect(filter).toBeUndefined();
  });

  it('should not include a version in the filter when its value is all', () => {
    const searchParams = new URLSearchParams();
    searchParams.append('evaluated', 'true');
    searchParams.append('version', 'all');

    const filter = parseDecisionInstancesSearchFilter(searchParams);

    expect(filter).toEqual({state: {$in: ['EVALUATED']}});
  });

  it('should not include a tenantId in the filter when its value is all', () => {
    const searchParams = new URLSearchParams();
    searchParams.append('evaluated', 'true');
    searchParams.append('tenant', 'all');

    const filter = parseDecisionInstancesSearchFilter(searchParams);

    expect(filter).toEqual({state: {$in: ['EVALUATED']}});
  });
});

describe('parseDecisionDefinitionsSearchFilter', () => {
  it('should parse decision definitions search filter from search params', () => {
    const searchParams = new URLSearchParams();
    searchParams.append('name', 'testName');
    searchParams.append('version', '3');
    searchParams.append('tenant', 'tenant-A');

    const filter = parseDecisionDefinitionsSearchFilter(searchParams);

    expect(filter).toEqual({
      decisionDefinitionId: 'testName',
      version: 3,
      tenantId: 'tenant-A',
    });
  });

  it('should return empty filters when no relevant param is set', () => {
    const searchParams = new URLSearchParams();
    searchParams.append('someParam', 'someValue');

    const filter = parseDecisionDefinitionsSearchFilter(searchParams);

    expect(filter).toEqual({});
  });

  it('should not include a version in the filter when its value is all', () => {
    const searchParams = new URLSearchParams();
    searchParams.append('name', 'testName');
    searchParams.append('version', 'all');

    const filter = parseDecisionDefinitionsSearchFilter(searchParams);

    expect(filter).toEqual({
      decisionDefinitionId: 'testName',
    });
  });

  it('should not include a tenantId in the filter when its value is all', () => {
    const searchParams = new URLSearchParams();
    searchParams.append('name', 'testName');
    searchParams.append('tenant', 'all');

    const filter = parseDecisionDefinitionsSearchFilter(searchParams);

    expect(filter).toEqual({
      decisionDefinitionId: 'testName',
    });
  });
});

describe('parseDecisionInstancesSearchSort', () => {
  it('should parse decision instances search sort from search params', () => {
    const searchParams = new URLSearchParams();
    searchParams.append('sort', 'decisionDefinitionName+asc');

    const sort = parseDecisionInstancesSearchSort(searchParams);

    expect(sort).toEqual([{field: 'decisionDefinitionName', order: 'asc'}]);
  });

  it('should return a default sort when no sort param is set', () => {
    const searchParams = new URLSearchParams();

    const sort = parseDecisionInstancesSearchSort(searchParams);

    expect(sort).toEqual([{field: 'evaluationDate', order: 'desc'}]);
  });
});
