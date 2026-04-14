/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type FilterOption = {
  id: string;
  textKey: string;
  searchParamValue: 'yes' | 'no' | undefined;
};

const START_FORM_FILTER_OPTIONS: FilterOption[] = [
  {
    id: 'ignore',
    textKey: 'processFiltersAllProcesses',
    searchParamValue: undefined,
  },
  {
    id: 'yes',
    textKey: 'processesFormFilterRequiresForm',
    searchParamValue: 'yes',
  },
  {
    id: 'no',
    textKey: 'processesFormFilterRequiresNoForm',
    searchParamValue: 'no',
  },
];

export {START_FORM_FILTER_OPTIONS};
export type {FilterOption};
