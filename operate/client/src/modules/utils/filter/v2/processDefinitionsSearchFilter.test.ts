/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {parseProcessDefinitionsSearchFilter} from './processDefinitionsSearchFilter';

describe('parseProcessDefinitionsSearchFilter', () => {
  it('should parse process definitions search filter from search params', () => {
    const searchParams = new URLSearchParams();
    searchParams.append('process', 'testProcess');
    searchParams.append('version', '3');
    searchParams.append('tenant', 'tenant-A');

    const filter = parseProcessDefinitionsSearchFilter(searchParams);

    expect(filter).toEqual({
      processDefinitionId: 'testProcess',
      version: 3,
      tenantId: 'tenant-A',
    });
  });

  it('should return empty filters when no relevant param is set', () => {
    const searchParams = new URLSearchParams();
    searchParams.append('someParam', 'someValue');

    const filter = parseProcessDefinitionsSearchFilter(searchParams);

    expect(filter).toEqual({});
  });

  it('should not include a version in the filter when its value is all', () => {
    const searchParams = new URLSearchParams();
    searchParams.append('process', 'testProcess');
    searchParams.append('version', 'all');

    const filter = parseProcessDefinitionsSearchFilter(searchParams);

    expect(filter).toEqual({
      processDefinitionId: 'testProcess',
    });
  });

  it('should not include a tenantId in the filter when its value is all', () => {
    const searchParams = new URLSearchParams();
    searchParams.append('process', 'testProcess');
    searchParams.append('tenant', 'all');

    const filter = parseProcessDefinitionsSearchFilter(searchParams);

    expect(filter).toEqual({
      processDefinitionId: 'testProcess',
    });
  });
});
