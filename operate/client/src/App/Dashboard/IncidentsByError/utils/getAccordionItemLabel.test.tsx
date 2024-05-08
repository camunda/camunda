/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getAccordionItemLabel} from './getAccordionItemLabel';

describe('getAccordionItemLabel', () => {
  it('should get label', () => {
    expect(getAccordionItemLabel({name: 'myProcess', version: 2})).toBe(
      'myProcess – Version 2',
    );
    expect(
      getAccordionItemLabel({
        name: 'myProcess',
        version: 2,
        tenant: 'Tenant A',
      }),
    ).toBe('myProcess – Version 2 – Tenant A');
  });
});
