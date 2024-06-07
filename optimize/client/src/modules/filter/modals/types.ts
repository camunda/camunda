/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
  filterType: ProcessFilter<T>['type'];
  filterData?: Omit<ProcessFilter<T>, 'filterLevel'>;
  reportIds?: string[];
  forceEnabled?: (...args: any[]) => boolean;
  getPretext?: (...args: any[]) => ReactNode;
  getPosttext?: (...args: any[]) => ReactNode;
  modalTitle?: ReactNode;
}
