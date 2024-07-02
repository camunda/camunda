/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import zod, {ZodSchema} from 'zod';

const TASK_OPENED_REF = zod.object({
  by: zod.enum(['user', 'auto-select']),
  position: zod.number(),
  filter: zod.string(),
  sorting: zod.enum(['creation', 'follow-up', 'due', 'completion']),
});

const TASK_EMPTY_PAGE_OPENED_REF = zod.object({
  by: zod.enum(['os-notification']),
});

type TaskOpenedRef = zod.infer<typeof TASK_OPENED_REF>;
type TaskEmptyPageOpenedRef = zod.infer<typeof TASK_EMPTY_PAGE_OPENED_REF>;

const TASK_OPENED_PREFIX = 'task-opened';
const TASK_EMPTY_PAGE_OPENED_PREFIX = 'task-empty-page-opened';

function encodeRefTagData<T extends object>(data: T): string {
  return btoa(JSON.stringify(data));
}

function decodeRefTagData<T>(
  str: string | null,
  prefix: string,
  schema: ZodSchema<T>,
): T | null {
  if (!str) {
    return null;
  }
  if (!str.startsWith(`${prefix}:`)) {
    return null;
  }
  const jsonString = str.slice(prefix.length + 1);
  try {
    const json = JSON.parse(atob(jsonString));
    const parsed = schema.safeParse(json);
    if (parsed.success) {
      return parsed.data;
    } else {
      return null;
    }
  } catch {
    return null;
  }
}

function encodeTaskOpenedRef(data: TaskOpenedRef): string {
  return `${TASK_OPENED_PREFIX}:${encodeRefTagData(data)}`;
}

function encodeTaskEmptyPageRef(data: TaskEmptyPageOpenedRef): string {
  return `${TASK_EMPTY_PAGE_OPENED_PREFIX}:${encodeRefTagData(data)}`;
}

function decodeTaskOpenedRef(str: string | null): TaskOpenedRef | null {
  return decodeRefTagData(str, TASK_OPENED_PREFIX, TASK_OPENED_REF);
}

function decodeTaskEmptyPageRef(
  str: string | null,
): TaskEmptyPageOpenedRef | null {
  return decodeRefTagData(
    str,
    TASK_EMPTY_PAGE_OPENED_PREFIX,
    TASK_EMPTY_PAGE_OPENED_REF,
  );
}

export {
  encodeTaskOpenedRef,
  decodeTaskOpenedRef,
  encodeTaskEmptyPageRef,
  decodeTaskEmptyPageRef,
};
