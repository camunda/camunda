/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  ElementInstanceInspection,
  ElementInstanceType,
  JobKind,
  WaitStateType,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {formatDate} from 'modules/utils/date';

function getWaitStateLabel(
  waitStates: ElementInstanceInspection[],
): string | null {
  if (waitStates.length === 0) {
    return null;
  }

  // Timer wait states should not show a label on the diagram overlay
  const nonTimerWaitStates = waitStates.filter(
    (ws) => ws.waitStateType !== 'TIMER',
  );

  if (nonTimerWaitStates.length === 0) {
    return null;
  }

  return 'Waiting';
}

function getWaitStateStatusItems(
  waitStates: ElementInstanceInspection[],
): Array<{key: string; text: string}> {
  const earliestTimerDueDate = getEarliestTimerDueDate(waitStates);
  let hasRenderedTimerStatus = false;

  return waitStates.flatMap((waitState) => {
    switch (waitState.waitStateType) {
      case 'MESSAGE': {
        const messageName =
          (waitState.details['messageName'] as string) ?? 'unknown';
        return {
          key: `${waitState.elementInstanceKey}-MESSAGE-${messageName}`,
          text: `Waiting for message: ${messageName}`,
        };
      }
      case 'TIMER': {
        if (hasRenderedTimerStatus) {
          return [];
        }
        hasRenderedTimerStatus = true;

        if (earliestTimerDueDate) {
          return {
            key: `TIMER-${earliestTimerDueDate}`,
            text: `Timer due: ${formatDate(earliestTimerDueDate)}`,
          };
        }

        return {
          key: 'TIMER-waiting',
          text: 'Waiting for timer',
        };
      }
      case 'SIGNAL': {
        const signalName =
          (waitState.details['signalName'] as string) ?? 'unknown';
        return {
          key: `${waitState.elementInstanceKey}-SIGNAL-${signalName}`,
          text: `Waiting for signal: ${signalName}`,
        };
      }
      case 'CONDITION': {
        return {
          key: `${waitState.elementInstanceKey}-CONDITION`,
          text: 'Waiting for condition',
        };
      }
      case 'JOB': {
        const jobType = (waitState.details['jobType'] as string) ?? 'unknown';
        const jobKind = waitState.details['jobKind'] as string | undefined;
        if (jobKind === 'EXECUTION_LISTENER' || jobKind === 'TASK_LISTENER') {
          return {
            key: `${waitState.elementInstanceKey}-JOB-${jobKind}-${jobType}`,
            text: `Waiting for ${jobKind === 'EXECUTION_LISTENER' ? 'execution listener' : 'task listener'}: ${jobType}`,
          };
        }
        return {
          key: `${waitState.elementInstanceKey}-JOB-${jobType}`,
          text: `Waiting for job: ${jobType}`,
        };
      }
      case 'CHILD_INSTANCE': {
        return {
          key: `${waitState.elementInstanceKey}-CHILD_INSTANCE`,
          text: 'Waiting for child instance to complete',
        };
      }
      default:
        return {
          key: `${waitState.elementInstanceKey}-WAITING`,
          text: 'Waiting',
        };
    }
  });
}

function getEarliestTimerDueDate(
  waitStates: ElementInstanceInspection[],
): string | null {
  const timerWaitStates = waitStates.filter(
    (ws) => ws.waitStateType === 'TIMER' && ws.details['dueDate'],
  );

  if (timerWaitStates.length === 0) {
    return null;
  }

  let earliestDueDate: string | null = null;
  let earliestDueDateInMillis = Number.POSITIVE_INFINITY;

  timerWaitStates.forEach((waitState) => {
    const dueDate = waitState.details['dueDate'] as string;
    const dueDateInMillis = Date.parse(dueDate);

    if (
      !Number.isNaN(dueDateInMillis) &&
      dueDateInMillis < earliestDueDateInMillis
    ) {
      earliestDueDateInMillis = dueDateInMillis;
      earliestDueDate = dueDate;
    }
  });

  return earliestDueDate;
}

function isBeforeAllExecutionListenerWaitState(
  item: ElementInstanceInspection,
): boolean {
  return (
    (item.elementType as ElementInstanceType) === 'MULTI_INSTANCE_BODY' &&
    (item.details['waitStateType'] as WaitStateType) === 'JOB' &&
    (item.details['jobKind'] as JobKind) === 'EXECUTION_LISTENER' &&
    item.details['listenerEventType'] === 'BEFORE_ALL'
    // TODO use ListenerEventType from camunda-api-zod-schemas/8.10 when published
  );
}

export {
  getWaitStateLabel,
  getWaitStateStatusItems,
  getEarliestTimerDueDate,
  isBeforeAllExecutionListenerWaitState,
};
