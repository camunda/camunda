/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import zod, {ZodSchema} from 'zod';

const TASK_OPENED_REF = zod.object({
  by: zod.enum(['user']),
  position: zod.number(),
  filter: zod.enum(['all-open', 'unassigned', 'assigned-to-me', 'completed']),
  sorting: zod.enum(['creation', 'follow-up', 'due', 'completion']),
});

type TaskOpenedRef = zod.infer<typeof TASK_OPENED_REF>;

function encodeRefTag<T extends object>(data: T): string {
  return btoa(JSON.stringify(data));
}

function decodeRefTag<T>(str: string | null, schema: ZodSchema<T>): T | null {
  if (!str) return null;
  try {
    const json = JSON.parse(atob(str));
    const parsed = schema.safeParse(json);
    if (parsed.success) {
      return parsed.data;
    } else {
      return null;
    }
  } catch (e) {
    return null;
  }
}

function encodeTaskOpenedRef(data: TaskOpenedRef): string {
  return encodeRefTag(data);
}

function decodeTaskOpenedRef(str: string | null): TaskOpenedRef | null {
  return decodeRefTag(str, TASK_OPENED_REF);
}

export {encodeTaskOpenedRef, decodeTaskOpenedRef};
