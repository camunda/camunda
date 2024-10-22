/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ReactNode} from 'react';

import {Definition, FilterType, ProcessFilter, Variable} from 'types';

export type MultipleVarialbeFilterConfig = {
  getValues: (
    name: string,
    type: string,
    valuesCount: number,
    valueFilter: string,
    definition: Definition
  ) => Promise<(string | number | boolean | null)[]>;
  getVariables: (definition: Definition) => Promise<Variable[]>;
};
export type FilterLevel = 'instance' | 'view';

export interface FilterProps<T extends FilterType> {
  addFilter: (filter: FilterProps<T>['filterData']) => void;
  className?: string;
  close: () => void;
  config?: MultipleVarialbeFilterConfig | undefined;
  definitions: Definition[];
  filterLevel: FilterLevel;
  filterType: T;
  filterData?: Omit<ProcessFilter<T>, 'filterLevel'>;
  reportIds?: string[];
  forceEnabled?: (...args: unknown[]) => boolean;
  getPretext?: (...args: unknown[]) => ReactNode;
  getPosttext?: (...args: unknown[]) => ReactNode;
  modalTitle?: ReactNode;
}
