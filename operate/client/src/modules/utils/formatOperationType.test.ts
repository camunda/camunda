/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {formatOperationType} from './formatOperationType';

describe('formatOperationType', () => {
  it('should return placeholder if type is null', () => {
    expect(formatOperationType(null)).toBe('--');
  });

  it('should return placeholder if type is undefined', () => {
    expect(formatOperationType(undefined)).toBe('--');
  });

  it('should return placeholder if type is empty', () => {
    expect(formatOperationType('')).toBe('--');
  });

  it('should return formatted type', () => {
    expect(formatOperationType('CANCEL_PROCESS_INSTANCE')).toBe(
      'Cancel Process Instance',
    );
  });
});
