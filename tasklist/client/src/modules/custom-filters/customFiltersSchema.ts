/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {isValidJSON} from 'modules/utils/isValidJSON';
import {z} from 'zod';

const customFiltersSchema = z.object({
  assignee: z
    .enum(['all', 'unassigned', 'me', 'user-and-group'])
    .default('all'),
  assignedTo: z.string().trim().optional(),
  candidateGroup: z.string().trim().optional(),
  status: z.enum(['all', 'open', 'completed']).default('all'),
  bpmnProcess: z.string().optional(),
  tenant: z.string().optional(),
  dueDateFrom: z.coerce.date().optional(),
  dueDateTo: z.coerce.date().optional(),
  followUpDateFrom: z.coerce.date().optional(),
  followUpDateTo: z.coerce.date().optional(),
  taskId: z.string().trim().optional(),
  variables: z
    .array(
      z
        .object({
          name: z.string().trim().optional(),
          value: z
            .string()
            .trim()
            .transform((value) =>
              isValidJSON(value)
                ? JSON.stringify(JSON.parse(value), null, 0)
                : value,
            )
            .optional(),
        })
        .superRefine(({name = '', value = ''}, ctx) => {
          const message = 'You must fill both name and value';

          if (name.length > 0 && value.length === 0) {
            ctx.addIssue({
              code: 'custom',
              message,
              path: ['value'],
            });
          }

          if (name.length === 0 && value.length > 0) {
            ctx.addIssue({
              code: 'custom',
              message,
              path: ['name'],
            });
          }
        }),
    )
    .transform((value) =>
      value.filter(
        ({name = '', value = ''}) => name.length > 0 && value.length > 0,
      ),
    )
    .optional(),
});

type CustomFilters = z.infer<typeof customFiltersSchema>;

export {customFiltersSchema};
export type {CustomFilters};
