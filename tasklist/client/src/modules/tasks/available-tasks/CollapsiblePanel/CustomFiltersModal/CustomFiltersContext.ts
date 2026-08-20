/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {NamedCustomFilters} from 'modules/tasks/filters/customFiltersSchema';
import {createContext, useContext} from 'react';

type CustomFiltersContextValue = {
  customFilters: Record<string, NamedCustomFilters>;
  status: 'initial' | 'adding' | `editing_${string}` | `deleting_${string}`;
  startEditing: (key: string) => void;
  startDeleting: (key: string) => void;
  startAdding: () => void;
  reset: () => void;
};

const CustomFiltersContext = createContext<CustomFiltersContextValue | null>(
  null,
);

function useCustomFiltersContext() {
  const context = useContext(CustomFiltersContext);

  if (context === null) {
    throw new Error(
      'useCustomFiltersContext must be used within a CustomFiltersProvider',
    );
  }

  return context;
}

export {CustomFiltersContext, useCustomFiltersContext};
export type {CustomFiltersContextValue};
