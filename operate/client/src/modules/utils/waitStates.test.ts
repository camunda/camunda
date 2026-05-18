/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect} from 'vitest';
import type {ElementInstanceInspection} from '@camunda/camunda-api-zod-schemas/8.10';
import {
  getWaitStateLabel,
  getWaitStateStatusItems,
  getEarliestTimerDueDate,
} from './waitStates';

const baseWaitState: ElementInstanceInspection = {
  rootProcessInstanceKey: '123',
  processInstanceKey: '123',
  elementInstanceKey: '456',
  elementId: 'task1',
  elementType: 'SERVICE_TASK',
  waitStateType: 'JOB',
  details: {},
};

describe('waitStates utils', () => {
  describe('getWaitStateLabel', () => {
    it('should return null for empty array', () => {
      expect(getWaitStateLabel([])).toBeNull();
    });

    it('should return "Waiting" for non-timer wait states', () => {
      expect(
        getWaitStateLabel([
          {
            ...baseWaitState,
            waitStateType: 'MESSAGE',
            details: {messageName: 'foo'},
          },
        ]),
      ).toBe('Waiting');
    });

    it('should return null when only timer wait states exist', () => {
      expect(
        getWaitStateLabel([
          {
            ...baseWaitState,
            waitStateType: 'TIMER',
            details: {dueDate: '2026-01-01T00:00:00Z'},
          },
        ]),
      ).toBeNull();
    });

    it('should return "Waiting" when mixed timer and non-timer wait states exist', () => {
      expect(
        getWaitStateLabel([
          {
            ...baseWaitState,
            waitStateType: 'TIMER',
            details: {dueDate: '2026-01-01T00:00:00Z'},
          },
          {
            ...baseWaitState,
            waitStateType: 'MESSAGE',
            details: {messageName: 'foo'},
          },
        ]),
      ).toBe('Waiting');
    });
  });

  describe('getWaitStateStatusItems', () => {
    it('should return empty array for no wait states', () => {
      expect(getWaitStateStatusItems([])).toEqual([]);
    });

    it('should format MESSAGE wait state', () => {
      const items = getWaitStateStatusItems([
        {
          ...baseWaitState,
          waitStateType: 'MESSAGE',
          details: {messageName: 'order-completion'},
        },
      ]);
      expect(items).toHaveLength(1);
      expect(items[0]!.text).toBe('Waiting for message: order-completion');
      expect(items[0]!.icon).toBe('message');
    });

    it('should format TIMER wait state with due date', () => {
      const items = getWaitStateStatusItems([
        {
          ...baseWaitState,
          waitStateType: 'TIMER',
          details: {dueDate: '2026-04-14T10:00:00Z'},
        },
      ]);
      expect(items).toHaveLength(1);
      expect(items[0]!.text).toContain('Timer due:');
      expect(items[0]!.icon).toBe('time');
    });

    it('should format TIMER wait state without due date', () => {
      const items = getWaitStateStatusItems([
        {...baseWaitState, waitStateType: 'TIMER', details: {}},
      ]);
      expect(items).toHaveLength(1);
      expect(items[0]!.text).toBe('Waiting for timer');
    });

    it('should format JOB wait state', () => {
      const items = getWaitStateStatusItems([
        {
          ...baseWaitState,
          waitStateType: 'JOB',
          details: {jobType: 'send-email'},
        },
      ]);
      expect(items).toHaveLength(1);
      expect(items[0]!.text).toBe('Waiting for job: send-email');
      expect(items[0]!.icon).toBe('job');
    });

    it('should format execution listener JOB wait state', () => {
      const items = getWaitStateStatusItems([
        {
          ...baseWaitState,
          waitStateType: 'JOB',
          details: {jobType: 'my-listener', jobKind: 'EXECUTION_LISTENER'},
        },
      ]);
      expect(items).toHaveLength(1);
      expect(items[0]!.text).toBe(
        'Waiting for execution listener: my-listener',
      );
    });

    it('should format SIGNAL wait state', () => {
      const items = getWaitStateStatusItems([
        {
          ...baseWaitState,
          waitStateType: 'SIGNAL',
          details: {signalName: 'abort'},
        },
      ]);
      expect(items).toHaveLength(1);
      expect(items[0]!.text).toBe('Waiting for signal: abort');
    });

    it('should format CONDITION wait state', () => {
      const items = getWaitStateStatusItems([
        {...baseWaitState, waitStateType: 'CONDITION', details: {}},
      ]);
      expect(items).toHaveLength(1);
      expect(items[0]!.text).toBe('Waiting for condition');
    });

    it('should format CHILD_INSTANCE wait state', () => {
      const items = getWaitStateStatusItems([
        {...baseWaitState, waitStateType: 'CHILD_INSTANCE', details: {}},
      ]);
      expect(items).toHaveLength(1);
      expect(items[0]!.text).toBe('Waiting for child instance to complete');
    });

    it('should handle multiple wait states', () => {
      const items = getWaitStateStatusItems([
        {
          ...baseWaitState,
          waitStateType: 'MESSAGE',
          details: {messageName: 'msg1'},
        },
        {
          ...baseWaitState,
          waitStateType: 'MESSAGE',
          details: {messageName: 'msg2'},
        },
      ]);
      expect(items).toHaveLength(2);
      expect(items[0]!.text).toBe('Waiting for message: msg1');
      expect(items[1]!.text).toBe('Waiting for message: msg2');
    });
  });

  describe('getEarliestTimerDueDate', () => {
    it('should return null for empty array', () => {
      expect(getEarliestTimerDueDate([])).toBeNull();
    });

    it('should return null when no timer wait states', () => {
      expect(
        getEarliestTimerDueDate([
          {...baseWaitState, waitStateType: 'MESSAGE', details: {}},
        ]),
      ).toBeNull();
    });

    it('should return the earliest due date', () => {
      expect(
        getEarliestTimerDueDate([
          {
            ...baseWaitState,
            waitStateType: 'TIMER',
            details: {dueDate: '2026-04-15T10:00:00Z'},
          },
          {
            ...baseWaitState,
            waitStateType: 'TIMER',
            details: {dueDate: '2026-04-14T10:00:00Z'},
          },
          {
            ...baseWaitState,
            waitStateType: 'TIMER',
            details: {dueDate: '2026-04-16T10:00:00Z'},
          },
        ]),
      ).toBe('2026-04-14T10:00:00Z');
    });

    it('should ignore timers without due date', () => {
      expect(
        getEarliestTimerDueDate([
          {...baseWaitState, waitStateType: 'TIMER', details: {}},
          {
            ...baseWaitState,
            waitStateType: 'TIMER',
            details: {dueDate: '2026-04-14T10:00:00Z'},
          },
        ]),
      ).toBe('2026-04-14T10:00:00Z');
    });
  });
});
