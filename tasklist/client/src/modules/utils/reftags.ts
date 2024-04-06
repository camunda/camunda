/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import zod, {ZodSchema} from 'zod';

const TASK_OPENED_REF = zod.object({
  by: zod.enum(['user', 'auto-select']),
  position: zod.number(),
  filter: zod.enum([
    'all-open',
    'unassigned',
    'assigned-to-me',
    'completed',
    'custom',
  ]),
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
