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

import {useEffect, useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import zod from 'zod';

const searchSchema = zod.tuple([zod.string(), zod.string()]);

const filtersSchema = zod.object({
  filter: zod
    .enum(['all-open', 'unassigned', 'assigned-to-me', 'completed', 'custom'])
    .default('all-open'),
  sortBy: zod
    .enum(['creation', 'follow-up', 'due', 'completion'])
    .default('creation'),
  sortOrder: zod.enum(['asc', 'desc']).default('desc'),
  state: zod.enum(['CREATED', 'COMPLETED', 'CANCELED']).optional(),
  assigned: zod
    .enum(['true', 'false'])
    .transform((value) => value === 'true')
    .optional(),
  assignee: zod.string().optional(),
  taskDefinitionId: zod.string().optional(),
  candidateGroup: zod.string().optional(),
  candidateUser: zod.string().optional(),
  processDefinitionKey: zod.string().optional(),
  processInstanceKey: zod.string().optional(),
  pageSize: zod.coerce.number().optional(),
  tenantIds: zod
    .string()
    .transform<string[] | undefined>((value) => {
      if (value === undefined) {
        return undefined;
      }
      const parsedValue = JSON.parse(value);

      if (Array.isArray(parsedValue)) {
        return parsedValue;
      }

      return [parsedValue];
    })
    .optional(),
  dueDateFrom: zod.coerce.date().optional(),
  dueDateTo: zod.coerce.date().optional(),
  followUpDateFrom: zod.coerce.date().optional(),
  followUpDateTo: zod.coerce.date().optional(),
  sort: zod
    .array(
      zod.object({
        field: zod.enum([
          'creationTime',
          'dueDate',
          'followUpDate',
          'completionTime',
        ]),
        order: zod.enum(['ASC', 'DESC']),
      }),
    )
    .optional(),
  searchAfter: searchSchema.optional(),
  searchAfterOrEqual: searchSchema.optional(),
  searchBefore: searchSchema.optional(),
  searchBeforeOrEqual: searchSchema.optional(),
});

const DEFAULT_FILTERS = filtersSchema.parse({});

type TaskFilters = zod.infer<typeof filtersSchema>;

function useTaskFilters(): TaskFilters {
  const [params, setSearchParams] = useSearchParams();
  const currentFilter = params.get('filter');
  const OLD_FILTERS = {
    'claimed-by-me': 'assigned-to-me',
    unclaimed: 'unassigned',
  } as const;

  useEffect(() => {
    if (
      currentFilter !== null &&
      Object.keys(OLD_FILTERS).includes(currentFilter)
    ) {
      params.set(
        'filter',
        OLD_FILTERS[currentFilter as keyof typeof OLD_FILTERS],
      );

      setSearchParams(params, {
        replace: true,
      });
    }
  });

  const queryString = params.toString();

  return useMemo<TaskFilters>(() => {
    const result = filtersSchema.safeParse(
      Object.fromEntries(new URLSearchParams(queryString).entries()),
    );
    const filters = result.success ? result.data : DEFAULT_FILTERS;

    return filters;
  }, [queryString]);
}

export {useTaskFilters, filtersSchema};
export type {TaskFilters};
