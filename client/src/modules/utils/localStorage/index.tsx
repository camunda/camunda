/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {customFiltersSchema} from 'modules/custom-filters/customFiltersSchema';
import {z} from 'zod';

const validators = {
  tenantId: z.string(),
  hasCompletedTask: z.boolean(),
  wasReloaded: z.boolean(),
  hasConsentedToStartProcess: z.boolean(),
  theme: z.enum(['light', 'dark', 'system']),
  autoSelectNextTask: z.boolean(),
  customFilters: z.object({
    custom: customFiltersSchema,
  }),
} as const;

type Validators = typeof validators;
type StorageKey = keyof Validators;

function storeStateLocally<Key extends StorageKey>(
  storageKey: Key,
  value: z.infer<Validators[Key]>,
) {
  localStorage.setItem(storageKey, JSON.stringify(value));
}

function clearStateLocally(storageKey: StorageKey) {
  localStorage.removeItem(storageKey);
}

function getStateLocally<Key extends StorageKey>(
  storageKey: Key,
): null | z.infer<Validators[Key]> {
  const value = localStorage.getItem(storageKey);

  if (value === null) {
    return null;
  }

  const result = validators[storageKey].safeParse(JSON.parse(value));

  return result.success ? result.data : null;
}

export {storeStateLocally, clearStateLocally, getStateLocally};
