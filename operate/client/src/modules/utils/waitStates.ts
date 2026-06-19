/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ElementInstanceInspection} from '@camunda/camunda-api-zod-schemas/8.10';
import {formatDate} from 'modules/utils/date';

function getWaitStateLabel(
  waitStates: ElementInstanceInspection[],
): string | null {
  if (waitStates.length === 0) {
    return null;
  }

  // Timer wait states should not show a label on the diagram overlay
  const nonTimerWaitStates = waitStates.filter(
    (ws) => ws.details.waitStateType !== 'TIMER',
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
    const {details} = waitState;

    if (details.waitStateType === 'MESSAGE') {
      const {messageName} = details;
      return {
        key: `${waitState.elementInstanceKey}-MESSAGE-${messageName}`,
        text: `Waiting for message: ${messageName}`,
      };
    }

    if (details.waitStateType === 'TIMER') {
      if (hasRenderedTimerStatus) {
        return [];
      }
      hasRenderedTimerStatus = true;

      if (earliestTimerDueDate !== null) {
        return {
          key: `TIMER-${earliestTimerDueDate}`,
          text: `Timer due: ${formatDate(new Date(earliestTimerDueDate))}`,
        };
      }

      return {
        key: 'TIMER-waiting',
        text: 'Waiting for timer',
      };
    }

    if (details.waitStateType === 'SIGNAL') {
      const {signalName} = details;
      return {
        key: `${waitState.elementInstanceKey}-SIGNAL-${signalName}`,
        text: `Waiting for signal: ${signalName}`,
      };
    }

    if (details.waitStateType === 'JOB') {
      const {jobType, jobKind} = details;
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

    if (details.waitStateType === 'USER_TASK') {
      const {taskKey} = details;
      return {
        key: `${waitState.elementInstanceKey}-USER_TASK-${taskKey}`,
        text: 'Waiting for user task',
      };
    }

    return {
      key: `${waitState.elementInstanceKey}-WAITING`,
      text: 'Waiting',
    };
  });
}

function getEarliestTimerDueDate(
  waitStates: ElementInstanceInspection[],
): number | null {
  let earliestDueDate: number | null = null;
  let earliestDueDateInMillis = Number.POSITIVE_INFINITY;

  for (const waitState of waitStates) {
    const {details} = waitState;

    if (details.waitStateType !== 'TIMER') {
      continue;
    }

    const {dueDate} = details;

    if (dueDate === null) {
      continue;
    }

    if (dueDate < earliestDueDateInMillis) {
      earliestDueDateInMillis = dueDate;
      earliestDueDate = dueDate;
    }
  }

  return earliestDueDate;
}

export {getWaitStateLabel, getWaitStateStatusItems, getEarliestTimerDueDate};
