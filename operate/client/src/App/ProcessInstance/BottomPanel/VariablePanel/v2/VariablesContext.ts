/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {InfiniteData} from '@tanstack/react-query';
import type {QueryVariablesResponseBody} from '@vzeta/camunda-api-zod-schemas';
import React from 'react';

export type VariablesDisplayStatus =
  | 'error'
  | 'no-content'
  | 'multi-instances'
  | 'no-variables'
  | 'skeleton'
  | 'spinner'
  | 'variables';

type VariablesContextType = {
  fetchNextPage: () => void;
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  status: VariablesDisplayStatus;
  variablesData?: InfiniteData<QueryVariablesResponseBody>;
};

export const VariablesContext =
  React.createContext<VariablesContextType | null>(null);

export const useVariablesContext = () => {
  const context = React.useContext(VariablesContext);
  if (!context) {
    throw new Error(
      'useVariablesContext must be used within a VariablesProvider',
    );
  }
  return context;
};
