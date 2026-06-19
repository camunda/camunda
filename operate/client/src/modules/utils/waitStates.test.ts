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
  tenantId: 'default',
  bpmnProcessId: 'process-1',
  details: {
    waitStateType: 'JOB',
    jobKey: '789',
    jobType: 'send-email',
    jobKind: 'BPMN_ELEMENT',
    listenerEventType: null,
    retries: 3,
  },
};

describe('getWaitStateLabel', () => {
  it('should return null for empty array', () => {
    expect(getWaitStateLabel([])).toBeNull();
  });

  it('should return "Waiting" for non-timer wait states', () => {
    expect(
      getWaitStateLabel([
        {
          ...baseWaitState,
          details: {
            waitStateType: 'MESSAGE',
            messageName: 'foo',
            correlationKey: null,
          },
        },
      ]),
    ).toBe('Waiting');
  });

  it('should return null when only timer wait states exist', () => {
    expect(
      getWaitStateLabel([
        {
          ...baseWaitState,
          details: {
            waitStateType: 'TIMER',
            dueDate: 1744617600000,
            repetitions: 0,
          },
        },
      ]),
    ).toBeNull();
  });

  it('should return "Waiting" when mixed timer and non-timer wait states exist', () => {
    expect(
      getWaitStateLabel([
        {
          ...baseWaitState,
          details: {
            waitStateType: 'TIMER',
            dueDate: 1744617600000,
            repetitions: 0,
          },
        },
        {
          ...baseWaitState,
          details: {
            waitStateType: 'MESSAGE',
            messageName: 'foo',
            correlationKey: null,
          },
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
        details: {
          waitStateType: 'MESSAGE',
          messageName: 'order-completion',
          correlationKey: null,
        },
      },
    ]);
    expect(items).toHaveLength(1);
    expect(items[0]!.text).toBe('Waiting for message: order-completion');
  });

  it('should format TIMER wait state with due date', () => {
    const items = getWaitStateStatusItems([
      {
        ...baseWaitState,
        details: {
          waitStateType: 'TIMER',
          dueDate: 1744617600000,
          repetitions: 0,
        },
      },
    ]);
    expect(items).toHaveLength(1);
    expect(items[0]!.text).toContain('Timer due:');
  });

  it('should format TIMER wait state without due date', () => {
    const items = getWaitStateStatusItems([
      {
        ...baseWaitState,
        details: {
          waitStateType: 'TIMER',
          dueDate: null,
          repetitions: 0,
        },
      },
    ]);
    expect(items).toHaveLength(1);
    expect(items[0]!.text).toBe('Waiting for timer');
  });

  it('should format JOB wait state', () => {
    const items = getWaitStateStatusItems([
      {
        ...baseWaitState,
        details: {
          waitStateType: 'JOB',
          jobKey: '789',
          jobType: 'send-email',
          jobKind: 'BPMN_ELEMENT',
          listenerEventType: null,
          retries: 3,
        },
      },
    ]);
    expect(items).toHaveLength(1);
    expect(items[0]!.text).toBe('Waiting for job: send-email');
  });

  it('should format execution listener JOB wait state', () => {
    const items = getWaitStateStatusItems([
      {
        ...baseWaitState,
        details: {
          waitStateType: 'JOB',
          jobKey: '789',
          jobType: 'my-listener',
          jobKind: 'EXECUTION_LISTENER',
          listenerEventType: 'START',
          retries: 3,
        },
      },
    ]);
    expect(items).toHaveLength(1);
    expect(items[0]!.text).toBe('Waiting for execution listener: my-listener');
  });

  it('should format SIGNAL wait state', () => {
    const items = getWaitStateStatusItems([
      {
        ...baseWaitState,
        details: {
          waitStateType: 'SIGNAL',
          signalName: 'abort',
        },
      },
    ]);
    expect(items).toHaveLength(1);
    expect(items[0]!.text).toBe('Waiting for signal: abort');
  });

  it('should format USER_TASK wait state', () => {
    const items = getWaitStateStatusItems([
      {
        ...baseWaitState,
        details: {
          waitStateType: 'USER_TASK',
          taskKey: '910',
          dueDate: null,
        },
      },
    ]);
    expect(items).toHaveLength(1);
    expect(items[0]!.text).toBe('Waiting for user task');
  });

  it('should handle multiple wait states', () => {
    const items = getWaitStateStatusItems([
      {
        ...baseWaitState,
        details: {
          waitStateType: 'MESSAGE',
          messageName: 'msg1',
          correlationKey: null,
        },
      },
      {
        ...baseWaitState,
        details: {
          waitStateType: 'MESSAGE',
          messageName: 'msg2',
          correlationKey: null,
        },
      },
    ]);
    expect(items).toHaveLength(2);
    expect(items[0]!.text).toBe('Waiting for message: msg1');
    expect(items[1]!.text).toBe('Waiting for message: msg2');
  });

  it('should collapse multiple TIMER wait states into one status item', () => {
    const items = getWaitStateStatusItems([
      {
        ...baseWaitState,
        details: {
          waitStateType: 'TIMER',
          dueDate: 1744704000000,
          repetitions: 0,
        },
      },
      {
        ...baseWaitState,
        details: {
          waitStateType: 'TIMER',
          dueDate: 1744617600000,
          repetitions: 0,
        },
      },
      {
        ...baseWaitState,
        details: {
          waitStateType: 'MESSAGE',
          messageName: 'msg1',
          correlationKey: null,
        },
      },
    ]);

    expect(items).toHaveLength(2);
    expect(items[0]!.text).toContain('Timer due:');
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
        {
          ...baseWaitState,
          details: {
            waitStateType: 'MESSAGE',
            messageName: 'msg',
            correlationKey: null,
          },
        },
      ]),
    ).toBeNull();
  });

  it('should return the earliest due date', () => {
    expect(
      getEarliestTimerDueDate([
        {
          ...baseWaitState,
          details: {
            waitStateType: 'TIMER',
            dueDate: 1744704000000,
            repetitions: 0,
          },
        },
        {
          ...baseWaitState,
          details: {
            waitStateType: 'TIMER',
            dueDate: 1744617600000,
            repetitions: 0,
          },
        },
        {
          ...baseWaitState,
          details: {
            waitStateType: 'TIMER',
            dueDate: 1744790400000,
            repetitions: 0,
          },
        },
      ]),
    ).toBe(1744617600000);
  });

  it('should ignore timers without due date', () => {
    expect(
      getEarliestTimerDueDate([
        {
          ...baseWaitState,
          details: {
            waitStateType: 'TIMER',
            dueDate: null,
            repetitions: 0,
          },
        },
        {
          ...baseWaitState,
          details: {
            waitStateType: 'TIMER',
            dueDate: 1744617600000,
            repetitions: 0,
          },
        },
      ]),
    ).toBe(1744617600000);
  });

  it('should compare due dates by numeric value', () => {
    expect(
      getEarliestTimerDueDate([
        {
          ...baseWaitState,
          details: {
            waitStateType: 'TIMER',
            dueDate: 200,
            repetitions: 0,
          },
        },
        {
          ...baseWaitState,
          details: {
            waitStateType: 'TIMER',
            dueDate: 100,
            repetitions: 0,
          },
        },
      ]),
    ).toBe(100);
  });
});
