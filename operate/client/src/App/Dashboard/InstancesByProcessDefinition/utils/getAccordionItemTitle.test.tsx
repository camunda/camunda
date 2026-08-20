/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getAccordionItemTitle} from './getAccordionItemTitle';

describe('getAccordionItemTitle', () => {
  it('should get title for multiple instances', () => {
    expect(
      getAccordionItemTitle({
        processName: 'myProcessName',
        instancesCount: 100,
        version: 3,
      }),
    ).toBe('View 100 Instances in Version 3 of Process myProcessName');
    expect(
      getAccordionItemTitle({
        processName: 'myProcessName',
        instancesCount: 100,
        version: 3,
        tenant: 'Tenant A',
      }),
    ).toBe(
      'View 100 Instances in Version 3 of Process myProcessName – Tenant A',
    );
  });

  it('should get title for single instance', () => {
    expect(
      getAccordionItemTitle({
        processName: 'myProcessName',
        instancesCount: 1,
        version: 2,
      }),
    ).toBe('View 1 Instance in Version 2 of Process myProcessName');
    expect(
      getAccordionItemTitle({
        processName: 'myProcessName',
        instancesCount: 1,
        version: 2,
        tenant: 'Tenant A',
      }),
    ).toBe('View 1 Instance in Version 2 of Process myProcessName – Tenant A');
  });

  it('should get title for no instances', () => {
    expect(
      getAccordionItemTitle({
        processName: 'myProcessName',
        instancesCount: 0,
        version: 6,
      }),
    ).toBe('View 0 Instances in Version 6 of Process myProcessName');
    expect(
      getAccordionItemTitle({
        processName: 'myProcessName',
        instancesCount: 0,
        version: 6,
        tenant: 'Tenant A',
      }),
    ).toBe('View 0 Instances in Version 6 of Process myProcessName – Tenant A');
  });
});
