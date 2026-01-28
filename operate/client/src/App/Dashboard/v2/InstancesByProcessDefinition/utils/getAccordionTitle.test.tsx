/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getAccordionTitle} from './getAccordionTitle';

describe('getAccordionTitle', () => {
  it('should get title for multiple versions with "2+" label', () => {
    expect(
      getAccordionTitle({
        processName: 'myProcessName',
        instancesCount: 100,
        hasMultipleVersions: true,
      }),
    ).toBe('View 100 Instances in 2+ Versions of Process myProcessName');
    expect(
      getAccordionTitle({
        processName: 'myProcessName',
        instancesCount: 100,
        hasMultipleVersions: true,
        tenant: 'Tenant A',
      }),
    ).toBe(
      'View 100 Instances in 2+ Versions of Process myProcessName – Tenant A',
    );
  });

  it('should get title for single instance/version', () => {
    expect(
      getAccordionTitle({
        processName: 'myProcessName',
        instancesCount: 1,
        hasMultipleVersions: false,
      }),
    ).toBe('View 1 Instance in 1 Version of Process myProcessName');
    expect(
      getAccordionTitle({
        processName: 'myProcessName',
        instancesCount: 1,
        hasMultipleVersions: false,
        tenant: 'Tenant A',
      }),
    ).toBe('View 1 Instance in 1 Version of Process myProcessName – Tenant A');
  });

  it('should get title for no instances', () => {
    expect(
      getAccordionTitle({
        processName: 'myProcessName',
        instancesCount: 0,
        hasMultipleVersions: false,
      }),
    ).toBe('View 0 Instances in 1 Version of Process myProcessName');
    expect(
      getAccordionTitle({
        processName: 'myProcessName',
        instancesCount: 0,
        hasMultipleVersions: false,
        tenant: 'Tenant A',
      }),
    ).toBe('View 0 Instances in 1 Version of Process myProcessName – Tenant A');
  });
});
