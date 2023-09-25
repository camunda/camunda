/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getAccordionLabel} from './getAccordionLabel';

describe('getAccordionLabel', () => {
  it('should get accordion label for multiple instances/versions', () => {
    expect(
      getAccordionLabel({
        name: 'myProcessName',
        instancesCount: 123,
        versionsCount: 5,
      }),
    ).toBe('myProcessName – 123 Instances in 5 Versions');
    expect(
      getAccordionLabel({
        name: 'myProcessName',
        instancesCount: 123,
        versionsCount: 5,
        tenant: 'Tenant A',
      }),
    ).toBe('myProcessName – 123 Instances in 5 Versions – Tenant A');
  });

  it('should get accordion label for single instance/version', () => {
    expect(
      getAccordionLabel({
        name: 'myProcessName',
        instancesCount: 1,
        versionsCount: 1,
      }),
    ).toBe('myProcessName – 1 Instance in 1 Version');
    expect(
      getAccordionLabel({
        name: 'myProcessName',
        instancesCount: 1,
        versionsCount: 1,
        tenant: 'Tenant A',
      }),
    ).toBe('myProcessName – 1 Instance in 1 Version – Tenant A');
  });

  it('should get accordion label for no instances/versions', () => {
    expect(
      getAccordionLabel({
        name: 'myProcessName',
        instancesCount: 0,
        versionsCount: 0,
      }),
    ).toBe('myProcessName – 0 Instances in 0 Versions');
    expect(
      getAccordionLabel({
        name: 'myProcessName',
        instancesCount: 0,
        versionsCount: 0,
        tenant: 'Tenant A',
      }),
    ).toBe('myProcessName – 0 Instances in 0 Versions – Tenant A');
  });
});
