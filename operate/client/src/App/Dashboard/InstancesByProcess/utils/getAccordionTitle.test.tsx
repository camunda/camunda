/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getAccordionTitle} from './getAccordionTitle';

describe('getAccordionTitle', () => {
  it('should get title for multiple instances/versions', () => {
    expect(
      getAccordionTitle({
        processName: 'myProcessName',
        instancesCount: 100,
        versionsCount: 3,
      }),
    ).toBe('View 100 Instances in 3 Versions of Process myProcessName');
    expect(
      getAccordionTitle({
        processName: 'myProcessName',
        instancesCount: 100,
        versionsCount: 3,
        tenant: 'Tenant A',
      }),
    ).toBe(
      'View 100 Instances in 3 Versions of Process myProcessName – Tenant A',
    );
  });

  it('should get title for single instance/version', () => {
    expect(
      getAccordionTitle({
        processName: 'myProcessName',
        instancesCount: 1,
        versionsCount: 1,
      }),
    ).toBe('View 1 Instance in 1 Version of Process myProcessName');
    expect(
      getAccordionTitle({
        processName: 'myProcessName',
        instancesCount: 1,
        versionsCount: 1,
        tenant: 'Tenant A',
      }),
    ).toBe('View 1 Instance in 1 Version of Process myProcessName – Tenant A');
  });

  it('should get title for no instances/versions', () => {
    expect(
      getAccordionTitle({
        processName: 'myProcessName',
        instancesCount: 0,
        versionsCount: 0,
      }),
    ).toBe('View 0 Instances in 0 Versions of Process myProcessName');
    expect(
      getAccordionTitle({
        processName: 'myProcessName',
        instancesCount: 0,
        versionsCount: 0,
        tenant: 'Tenant A',
      }),
    ).toBe(
      'View 0 Instances in 0 Versions of Process myProcessName – Tenant A',
    );
  });
});
