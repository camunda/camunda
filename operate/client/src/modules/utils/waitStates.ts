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
    (ws) => ws.waitStateType !== 'TIMER',
  );

  if (nonTimerWaitStates.length === 0) {
    return null;
  }

  return 'Waiting';
}

function getWaitStateStatusItems(
  waitStates: ElementInstanceInspection[],
): Array<{
  icon: 'time' | 'message' | 'signal' | 'condition' | 'job';
  text: string;
}> {
  return waitStates.map((waitState) => {
    switch (waitState.waitStateType) {
      case 'MESSAGE': {
        const messageName =
          (waitState.details['messageName'] as string) ?? 'unknown';
        return {
          icon: 'message' as const,
          text: `Waiting for message: ${messageName}`,
        };
      }
      case 'TIMER': {
        const dueDate = waitState.details['dueDate'] as string | undefined;
        if (dueDate) {
          return {
            icon: 'time' as const,
            text: `Timer due: ${formatDate(dueDate)}`,
          };
        }
        return {
          icon: 'time' as const,
          text: 'Waiting for timer',
        };
      }
      case 'SIGNAL': {
        const signalName =
          (waitState.details['signalName'] as string) ?? 'unknown';
        return {
          icon: 'time' as const,
          text: `Waiting for signal: ${signalName}`,
        };
      }
      case 'CONDITION': {
        return {
          icon: 'condition' as const,
          text: 'Waiting for condition',
        };
      }
      case 'JOB': {
        const jobType = (waitState.details['jobType'] as string) ?? 'unknown';
        const jobKind = waitState.details['jobKind'] as string | undefined;
        if (jobKind === 'EXECUTION_LISTENER' || jobKind === 'TASK_LISTENER') {
          return {
            icon: 'job' as const,
            text: `Waiting for ${jobKind === 'EXECUTION_LISTENER' ? 'execution listener' : 'task listener'}: ${jobType}`,
          };
        }
        return {
          icon: 'job' as const,
          text: `Waiting for job: ${jobType}`,
        };
      }
      case 'CHILD_INSTANCE': {
        return {
          icon: 'time' as const,
          text: 'Waiting for child instance to complete',
        };
      }
      default:
        return {
          icon: 'time' as const,
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

  const dates = timerWaitStates
    .map((ws) => ws.details['dueDate'] as string)
    .sort();

  return dates[0] ?? null;
}

export {getWaitStateLabel, getWaitStateStatusItems, getEarliestTimerDueDate};
