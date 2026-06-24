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
        processName: 'myProcess',
        instancesCount: 100,
        versionName: 3,
        errorMessage: 'bad error',
      }),
    ).toBe(
      'View 100 Instances with error bad error in version 3 of Process myProcess',
    );
    expect(
      getAccordionItemTitle({
        processName: 'myProcess',
        instancesCount: 100,
        versionName: 3,
        errorMessage: 'bad error',
        tenant: 'Tenant A',
      }),
    ).toBe(
      'View 100 Instances with error bad error in version 3 of Process myProcess – Tenant A',
    );
  });

  it('should get title for single instance', () => {
    expect(
      getAccordionItemTitle({
        processName: 'myProcess',
        instancesCount: 1,
        versionName: 1,
        errorMessage: 'bad error',
      }),
    ).toBe(
      'View 1 Instance with error bad error in version 1 of Process myProcess',
    );
    expect(
      getAccordionItemTitle({
        processName: 'myProcess',
        instancesCount: 1,
        versionName: 1,
        errorMessage: 'bad error',
        tenant: 'Tenant A',
      }),
    ).toBe(
      'View 1 Instance with error bad error in version 1 of Process myProcess – Tenant A',
    );
  });

  it('should get title for no instances', () => {
    expect(
      getAccordionItemTitle({
        processName: 'myProcess',
        instancesCount: 0,
        versionName: 2,
        errorMessage: 'bad error',
      }),
    ).toBe(
      'View 0 Instances with error bad error in version 2 of Process myProcess',
    );
    expect(
      getAccordionItemTitle({
        processName: 'myProcess',
        instancesCount: 0,
        versionName: 2,
        errorMessage: 'bad error',
        tenant: 'Tenant A',
      }),
    ).toBe(
      'View 0 Instances with error bad error in version 2 of Process myProcess – Tenant A',
    );
  });
});
