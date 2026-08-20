/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getAccordionTitle} from './getAccordionTitle';

describe('getAccordionTitle', () => {
  it('should get title for multiple instances/versions', () => {
    expect(getAccordionTitle(100, 'no memory left')).toBe(
      'View 100 Instances with error no memory left',
    );
  });

  it('should get title for single instance/version', () => {
    expect(getAccordionTitle(1, 'no space left')).toBe(
      'View 1 Instance with error no space left',
    );
  });

  it('should get title for no instances/versions', () => {
    expect(getAccordionTitle(0, 'cannot connect')).toBe(
      'View 0 Instances with error cannot connect',
    );
  });
});
