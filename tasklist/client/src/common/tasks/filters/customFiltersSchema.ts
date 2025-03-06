/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isValidJSON} from 'common/utils/isValidJSON';
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

const namedCustomFiltersSchema = customFiltersSchema.merge(
  z.object({
    name: z.string().trim().optional(),
  }),
);

type CustomFilters = z.infer<typeof customFiltersSchema>;
type NamedCustomFilters = z.infer<typeof namedCustomFiltersSchema>;

export {customFiltersSchema, namedCustomFiltersSchema};
export type {CustomFilters, NamedCustomFilters};
