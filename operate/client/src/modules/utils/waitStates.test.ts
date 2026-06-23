/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect} from 'vitest';
import type {
  ElementInstanceInspection,
  WaitStateDetails,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {
  getWaitStateLabel,
  getWaitStateStatusItems,
  getEarliestTimerDueDate,
} from './waitStates';

function buildWaitState(details: WaitStateDetails): ElementInstanceInspection {
  return {
    rootProcessInstanceKey: '123',
    processInstanceKey: '123',
    elementInstanceKey: '456',
    elementId: 'task1',
    elementType: 'SERVICE_TASK',
    tenantId: '<default>',
    bpmnProcessId: 'process-1',
    details,
  };
}

const jobDetails: WaitStateDetails = {
  waitStateType: 'JOB',
  jobKey: '789',
  jobType: 'send-email',
  jobKind: 'BPMN_ELEMENT',
  listenerEventType: null,
  retries: null,
};

describe('getWaitStateLabel', () => {
  it('should return null for empty array', () => {
    expect(getWaitStateLabel([])).toBeNull();
  });

  it('should return "Waiting" for non-timer wait states', () => {
    expect(
      getWaitStateLabel([
        buildWaitState({
          waitStateType: 'MESSAGE',
          messageName: 'foo',
          correlationKey: null,
        }),
      ]),
    ).toBe('Waiting');
  });

  it('should return null when only timer wait states exist', () => {
    expect(
      getWaitStateLabel([
        buildWaitState({
          waitStateType: 'TIMER',
          dueDate: Date.parse('2026-01-01T00:00:00Z'),
          repetitions: null,
        }),
      ]),
    ).toBeNull();
  });

  it('should return "Waiting" when mixed timer and non-timer wait states exist', () => {
    expect(
      getWaitStateLabel([
        buildWaitState({
          waitStateType: 'TIMER',
          dueDate: Date.parse('2026-01-01T00:00:00Z'),
          repetitions: null,
        }),
        buildWaitState({
          waitStateType: 'MESSAGE',
          messageName: 'foo',
          correlationKey: null,
        }),
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
      buildWaitState({
        waitStateType: 'MESSAGE',
        messageName: 'order-completion',
        correlationKey: null,
      }),
    ]);
    expect(items).toHaveLength(1);
    expect(items[0]!.text).toBe('Waiting for message: order-completion');
  });

  it('should format TIMER wait state with due date', () => {
    const items = getWaitStateStatusItems([
      buildWaitState({
        waitStateType: 'TIMER',
        dueDate: Date.parse('2026-04-14T10:00:00Z'),
        repetitions: null,
      }),
    ]);
    expect(items).toHaveLength(1);
    expect(items[0]!.text).toContain('Waiting for timer: due date');
  });

  it('should format TIMER wait state without due date', () => {
    const items = getWaitStateStatusItems([
      buildWaitState({
        waitStateType: 'TIMER',
        dueDate: null,
        repetitions: null,
      }),
    ]);
    expect(items).toHaveLength(1);
    expect(items[0]!.text).toBe('Waiting for timer');
  });

  it('should format JOB wait state', () => {
    const items = getWaitStateStatusItems([buildWaitState(jobDetails)]);
    expect(items).toHaveLength(1);
    expect(items[0]!.text).toBe('Waiting for job: send-email');
  });

  it('should format execution listener JOB wait state', () => {
    const items = getWaitStateStatusItems([
      buildWaitState({
        ...jobDetails,
        jobType: 'my-listener',
        jobKind: 'EXECUTION_LISTENER',
      }),
    ]);
    expect(items).toHaveLength(1);
    expect(items[0]!.text).toBe('Waiting for execution listener: my-listener');
  });

  it('should format USER_TASK wait state', () => {
    const items = getWaitStateStatusItems([
      buildWaitState({
        waitStateType: 'USER_TASK',
        taskKey: '999',
        dueDate: null,
      }),
    ]);
    expect(items).toHaveLength(1);
    expect(items[0]!.text).toBe('Waiting for task completion');
  });

  it('should format SIGNAL wait state', () => {
    const items = getWaitStateStatusItems([
      buildWaitState({waitStateType: 'SIGNAL', signalName: 'abort'}),
    ]);
    expect(items).toHaveLength(1);
    expect(items[0]!.text).toBe('Waiting for signal: abort');
  });

  it('should format CONDITION wait state', () => {
    const items = getWaitStateStatusItems([
      buildWaitState({
        waitStateType: 'CONDITION',
        expression: '=foo > 1',
        events: ['bar'],
      }),
    ]);
    expect(items).toHaveLength(1);
    expect(items[0]!.text).toBe('Waiting for condition');
  });

  it('should handle multiple wait states', () => {
    const items = getWaitStateStatusItems([
      buildWaitState({
        waitStateType: 'MESSAGE',
        messageName: 'msg1',
        correlationKey: null,
      }),
      buildWaitState({
        waitStateType: 'MESSAGE',
        messageName: 'msg2',
        correlationKey: null,
      }),
    ]);
    expect(items).toHaveLength(2);
    expect(items[0]!.text).toBe('Waiting for message: msg1');
    expect(items[1]!.text).toBe('Waiting for message: msg2');
  });

  it('should collapse multiple TIMER wait states into one status item', () => {
    const items = getWaitStateStatusItems([
      buildWaitState({
        waitStateType: 'TIMER',
        dueDate: Date.parse('2026-04-15T10:00:00Z'),
        repetitions: null,
      }),
      buildWaitState({
        waitStateType: 'TIMER',
        dueDate: Date.parse('2026-04-14T10:00:00Z'),
        repetitions: null,
      }),
      buildWaitState({
        waitStateType: 'MESSAGE',
        messageName: 'msg1',
        correlationKey: null,
      }),
    ]);

    expect(items).toHaveLength(2);
    expect(items[0]!.text).toContain('Waiting for timer: due date');
    expect(items[1]!.text).toBe('Waiting for message: msg1');
  });
});

describe('getEarliestTimerDueDate', () => {
  it('should return null for empty array', () => {
    expect(getEarliestTimerDueDate([])).toBeNull();
  });

  it('should return null when no timer wait states', () => {
    expect(
      getEarliestTimerDueDate([
        buildWaitState({
          waitStateType: 'MESSAGE',
          messageName: 'foo',
          correlationKey: null,
        }),
      ]),
    ).toBeNull();
  });

  it('should return the earliest due date', () => {
    expect(
      getEarliestTimerDueDate([
        buildWaitState({
          waitStateType: 'TIMER',
          dueDate: Date.parse('2026-04-15T10:00:00Z'),
          repetitions: null,
        }),
        buildWaitState({
          waitStateType: 'TIMER',
          dueDate: Date.parse('2026-04-14T10:00:00Z'),
          repetitions: null,
        }),
        buildWaitState({
          waitStateType: 'TIMER',
          dueDate: Date.parse('2026-04-16T10:00:00Z'),
          repetitions: null,
        }),
      ]),
    ).toBe(Date.parse('2026-04-14T10:00:00Z'));
  });

  it('should ignore timers without due date', () => {
    expect(
      getEarliestTimerDueDate([
        buildWaitState({
          waitStateType: 'TIMER',
          dueDate: null,
          repetitions: null,
        }),
        buildWaitState({
          waitStateType: 'TIMER',
          dueDate: Date.parse('2026-04-14T10:00:00Z'),
          repetitions: null,
        }),
      ]),
    ).toBe(Date.parse('2026-04-14T10:00:00Z'));
  });
});
