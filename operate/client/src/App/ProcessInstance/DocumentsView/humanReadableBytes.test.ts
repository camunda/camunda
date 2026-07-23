/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {toHumanReadableBytes} from './humanReadableBytes';

describe('toHumanReadableBytes', () => {
  it('should handle zero bytes', () => {
    expect(toHumanReadableBytes(0)).toBe('0 B');
  });

  it('should handle invalid input', () => {
    expect(toHumanReadableBytes(NaN)).toBe('N/A');
    expect(toHumanReadableBytes(Infinity)).toBe('N/A');
  });

  it('should format bytes correctly', () => {
    expect(toHumanReadableBytes(1024)).toBe('1 KiB');
    expect(toHumanReadableBytes(1024 * 1024)).toBe('1 MiB');
    expect(toHumanReadableBytes(1024 * 1024 * 1024)).toBe('1 GiB');
  });

  it('should format decimals correctly', () => {
    expect(toHumanReadableBytes(1536)).toBe('1.5 KiB');
    expect(toHumanReadableBytes(2560)).toBe('2.5 KiB');
    expect(toHumanReadableBytes(1536 * 1024)).toBe('1.5 MiB');
    expect(toHumanReadableBytes(1280)).toBe('1.25 KiB');
  });

  it('should handle small values', () => {
    expect(toHumanReadableBytes(1)).toBe('1 B');
    expect(toHumanReadableBytes(512)).toBe('512 B');
  });

  it('should cap large values at GiB', () => {
    const largeValue = Math.pow(1024, 4);
    expect(toHumanReadableBytes(largeValue)).toBe('1024 GiB');
  });
});
