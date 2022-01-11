/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeAutoObservable} from 'mobx';

type State = {
  visibleFilters: Array<OptionalFilter>;
};

type OptionalFilter =
  | 'variable'
  | 'ids'
  | 'parentInstanceId'
  | 'operationId'
  | 'errorMessage'
  | 'startDate'
  | 'endDate';

const optionalFilters: Array<OptionalFilter> = [
  'variable',
  'ids',
  'parentInstanceId',
  'operationId',
  'errorMessage',
  'startDate',
  'endDate',
];

const DEFAULT_STATE: State = {
  visibleFilters: [],
};

class VisibleFilters {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  addVisibleFilters = (filters: Array<OptionalFilter>) => {
    this.state.visibleFilters = [
      ...new Set([...this.state.visibleFilters, ...filters]),
    ];
  };

  hideFilter = (filter: OptionalFilter) => {
    this.state.visibleFilters = this.state.visibleFilters.filter(
      (visibleFilter) => visibleFilter !== filter
    );
  };

  get areAllFiltersVisible() {
    return optionalFilters.every((optionalFilter) =>
      this.state.visibleFilters.includes(optionalFilter)
    );
  }

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const visibleFiltersStore = new VisibleFilters();
export {optionalFilters};
export type {OptionalFilter};
