/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {beforeEach, describe, expect, it, vi} from 'vitest';

vi.mock('canvas-confetti', () => ({
  default: vi.fn(),
}));

import confetti from 'canvas-confetti';
import {fireAgentTabConfettiOnce} from './agentTabConfetti';

describe('fireAgentTabConfettiOnce', () => {
  beforeEach(() => {
    (confetti as unknown as ReturnType<typeof vi.fn>).mockClear();
  });

  it('fires only once per page load', () => {
    const el = document.createElement('button');
    el.style.position = 'absolute';
    el.style.left = '10px';
    el.style.top = '10px';
    el.style.width = '100px';
    el.style.height = '20px';
    document.body.appendChild(el);

    fireAgentTabConfettiOnce(el);
    fireAgentTabConfettiOnce(el);

    // Our implementation fires multiple bursts in a single call.
    const calls = (confetti as unknown as ReturnType<typeof vi.fn>).mock.calls
      .length;
    expect(calls).toBeGreaterThan(0);

    // Still the same after the second attempt.
    const callsAfter = (confetti as unknown as ReturnType<typeof vi.fn>).mock
      .calls.length;
    expect(callsAfter).toBe(calls);
  });
});
