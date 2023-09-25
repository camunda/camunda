/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getAccordionItemLabel} from './getAccordionItemLabel';

describe('getAccordionItemLabel', () => {
  it('should get label for multiple instances', () => {
    expect(
      getAccordionItemLabel({
        name: 'myProcessName',
        instancesCount: 77,
        version: 3,
      }),
    ).toBe('myProcessName – 77 Instances in Version 3');
    expect(
      getAccordionItemLabel({
        name: 'myProcessName',
        instancesCount: 77,
        version: 3,
        tenant: 'Tenant A',
      }),
    ).toBe('myProcessName – 77 Instances in Version 3 – Tenant A');
  });

  it('should get label for single instance', () => {
    expect(
      getAccordionItemLabel({
        name: 'myProcessName',
        instancesCount: 1,
        version: 5,
      }),
    ).toBe('myProcessName – 1 Instance in Version 5');
    expect(
      getAccordionItemLabel({
        name: 'myProcessName',
        instancesCount: 1,
        version: 5,
        tenant: 'Tenant A',
      }),
    ).toBe('myProcessName – 1 Instance in Version 5 – Tenant A');
  });

  it('should get label for no instances', () => {
    expect(
      getAccordionItemLabel({
        name: 'myProcessName',
        instancesCount: 0,
        version: 2,
      }),
    ).toBe('myProcessName – 0 Instances in Version 2');
    expect(
      getAccordionItemLabel({
        name: 'myProcessName',
        instancesCount: 0,
        version: 2,
        tenant: 'Tenant A',
      }),
    ).toBe('myProcessName – 0 Instances in Version 2 – Tenant A');
  });
});
