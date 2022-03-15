/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {action, computed, makeAutoObservable} from 'mobx';

interface State<PossibleFilters extends string> {
  visibleFilters: PossibleFilters[];
}

const DEFAULT_STATE = {
  visibleFilters: [],
};

class VisibleFilters<PossibleFilters extends string> {
  state: State<PossibleFilters> = {...DEFAULT_STATE};
  possibleOptionalFilters: PossibleFilters[] = [];

  constructor(possibleOptionalFilters: PossibleFilters[] = []) {
    makeAutoObservable(this, {
      addVisibleFilters: action,
      hideFilter: action,
      areAllFiltersVisible: computed,
      reset: action,
    });

    this.possibleOptionalFilters = possibleOptionalFilters;
  }

  addVisibleFilters = (filters: PossibleFilters[]) => {
    this.state.visibleFilters = Array.from(
      new Set([...this.state.visibleFilters, ...filters])
    );
  };

  hideFilter = (filter: PossibleFilters) => {
    this.state.visibleFilters = this.state.visibleFilters.filter(
      (visibleFilter) => visibleFilter !== filter
    );
  };

  get areAllFiltersVisible() {
    return this.possibleOptionalFilters.every((possibleOptionalFilter) =>
      this.state.visibleFilters.includes(possibleOptionalFilter)
    );
  }

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export {VisibleFilters};
