/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  observable,
  decorate,
  action,
  computed,
  observe,
  reaction,
  when,
} from 'mobx';
import {DEFAULT_FIRST_ELEMENT, DEFAULT_SORTING} from 'modules/constants';
import {formatGroupedWorkflows} from 'modules/utils/instance';
import {
  parseFilterForRequest,
  parseQueryString,
  getWorkflowByVersion,
  getFilterWithWorkflowIds,
  decodeFields,
} from 'modules/utils/filter';
import {fetchGroupedWorkflows} from 'modules/api/instances';
import {isEqual, isEmpty} from 'lodash';
import {sanitizeFilter} from './utils/sanitizeFilter';
import {setBrowserUrl} from './utils/setBrowserUrl';

const DEFAULT_STATE = {
  filter: {},
  page: 1,
  entriesPerPage: 0,
  prevEntriesPerPage: 0,
  firstElement: DEFAULT_FIRST_ELEMENT,
  sorting: DEFAULT_SORTING,
  groupedWorkflows: {},
  batchOperationId: '',
  history: null,
  location: null,
  isInitialLoadComplete: false,
};

class Filters {
  state = {...DEFAULT_STATE};
  filterObserveDisposer = null;
  locationReactionDisposer = null;

  async init() {
    this.setGroupedWorkflows(
      formatGroupedWorkflows(await fetchGroupedWorkflows())
    );

    when(
      () => this.state.location !== null,
      () => {
        this.setFilterFromUrl();
        const queryParams = parseQueryString(this.state.location.search);

        if (!isEqual(this.state.filter, queryParams.filter)) {
          setBrowserUrl(
            this.state.history,
            this.state.location,
            this.state.filter,
            this.state.groupedWorkflows,
            queryParams.name
          );
        }

        this.completeInitialLoad();
      }
    );

    this.locationReactionDisposer = reaction(
      () => this.state.location,
      () => {
        this.setFilterFromUrl();
      }
    );

    this.filterObserveDisposer = observe(this.state, 'filter', (change) => {
      if (isEqual(this.state.filter, change.oldValue)) {
        return;
      }

      setBrowserUrl(
        this.state.history,
        this.state.location,
        this.state.filter,
        this.state.groupedWorkflows
      );
      this.setPage(1);
    });
  }

  completeInitialLoad = () => {
    this.state.isInitialLoadComplete = true;
  };

  setUrlParameters = (history, location) => {
    this.state.history = history;
    this.state.location = location;
  };

  setGroupedWorkflows = (groupedWorkflows) => {
    this.state.groupedWorkflows = groupedWorkflows;
  };

  setFilter = (filter) => {
    if (
      !filter.canceled &&
      !filter.completed &&
      this.state.sorting.sortBy === 'endDate'
    ) {
      this.setSorting(DEFAULT_SORTING);
    }

    this.state.filter = filter;
  };

  setFilterFromUrl() {
    const filterFromURL = sanitizeFilter(
      parseQueryString(this.state.location.search).filter,
      this.state.groupedWorkflows
    );

    if (!isEqual(this.state.filter, filterFromURL)) {
      this.state.filter = filterFromURL;
    }
  }

  setPage(page) {
    this.state.page = page;
  }

  setSorting(sorting) {
    this.state.sorting = sorting;
  }

  setEntriesPerPage(entriesPerPage) {
    this.state.prevEntriesPerPage = this.state.entriesPerPage;
    this.state.entriesPerPage = entriesPerPage;
  }

  getFiltersPayload() {
    const {filter, groupedWorkflows} = this.state;
    return parseFilterForRequest(
      getFilterWithWorkflowIds(filter, groupedWorkflows)
    );
  }

  get decodedFilters() {
    return decodeFields(this.state.filter);
  }

  get firstElement() {
    return (this.state.page - 1) * this.state.entriesPerPage;
  }

  get isNoVersionSelected() {
    return this.state.filter?.version === 'all';
  }

  get isNoWorkflowSelected() {
    return !this.state.filter?.workflow;
  }

  get workflow() {
    const {groupedWorkflows, filter} = this.state;
    return getWorkflowByVersion(
      groupedWorkflows[filter.workflow],
      filter.version
    );
  }

  get workflowName() {
    const workflow = !isEmpty(this.workflow)
      ? this.workflow
      : this.state.groupedWorkflows[this.state.filter.workflow];

    return workflow ? workflow.name || workflow.bpmnProcessId : 'Workflow';
  }

  reset = () => {
    this.state = {...DEFAULT_STATE};

    if (this.filterObserveDisposer !== null) {
      this.filterObserveDisposer();
    }
    if (this.locationReactionDisposer !== null) {
      this.locationReactionDisposer();
    }
  };
}

decorate(Filters, {
  state: observable,
  setFilterFromUrl: action,
  setFilter: action,
  setPage: action,
  setSorting: action,
  setEntriesPerPage: action,
  reset: action,
  firstElement: computed,
  decodedFilters: computed,
  isNoVersionSelected: computed,
  isNoWorkflowSelected: computed,
  workflow: computed,
  workflowName: computed,
  setGroupedWorkflows: action,
  completeInitialLoad: action,
  setUrlParameters: action,
});

export const filters = new Filters();
