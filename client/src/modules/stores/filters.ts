/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  makeAutoObservable,
  observe,
  reaction,
  when,
  Lambda,
  IReactionDisposer,
} from 'mobx';
import {DEFAULT_SORTING} from 'modules/constants';
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

type State = {
  filter: unknown;
  sorting: {
    sortBy: string;
    sortOrder: 'desc' | 'asc';
  };
  groupedWorkflows: unknown;
  batchOperationId: string;
  history: null | unknown;
  location: null | unknown;
  isInitialLoadComplete: boolean;
};

const DEFAULT_STATE: State = {
  filter: {},
  sorting: DEFAULT_SORTING,
  groupedWorkflows: {},
  batchOperationId: '',
  history: null,
  location: null,
  isInitialLoadComplete: false,
};

class Filters {
  state: State = {...DEFAULT_STATE};
  filterObserveDisposer: null | Lambda = null;
  locationReactionDisposer: null | IReactionDisposer = null;

  constructor() {
    makeAutoObservable(this);
  }

  async init() {
    try {
      const response = await fetchGroupedWorkflows();
      if (response.ok) {
        this.setGroupedWorkflows(formatGroupedWorkflows(await response.json()));
      }
    } catch {
      this.setGroupedWorkflows(formatGroupedWorkflows([]));
    }

    when(
      () => this.state.location !== null,
      () => {
        this.setFilterFromUrl();
        // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
        const queryParams = parseQueryString(this.state.location.search);

        // @ts-expect-error ts-migrate(2339) FIXME: Property 'filter' does not exist on type '{}'.
        if (!isEqual(this.state.filter, queryParams.filter)) {
          setBrowserUrl(
            this.state.history,
            this.state.location,
            this.state.filter,
            this.state.groupedWorkflows,
            // @ts-expect-error ts-migrate(2339) FIXME: Property 'name' does not exist on type '{}'.
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

      // @ts-expect-error ts-migrate(2554) FIXME: Expected 5 arguments, but got 4.
      setBrowserUrl(
        this.state.history,
        this.state.location,
        this.state.filter,
        this.state.groupedWorkflows
      );
    });
  }

  completeInitialLoad = () => {
    this.state.isInitialLoadComplete = true;
  };

  setUrlParameters = (history: any, location: any) => {
    this.state.history = history;
    this.state.location = location;
  };

  setGroupedWorkflows = (groupedWorkflows: any) => {
    this.state.groupedWorkflows = groupedWorkflows;
  };

  setFilter = (filter: any) => {
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
      // @ts-expect-error ts-migrate(2531) FIXME: Object is possibly 'null'.
      parseQueryString(this.state.location.search).filter,
      this.state.groupedWorkflows
    );

    if (!isEqual(this.state.filter, filterFromURL)) {
      this.state.filter = filterFromURL;
    }
  }

  setSorting(sorting: any) {
    this.state.sorting = sorting;
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

  get isNoVersionSelected() {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'version' does not exist on type '{}'.
    return this.state.filter?.version === 'all';
  }

  get isNoWorkflowSelected() {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'workflow' does not exist on type '{}'.
    return !this.state.filter?.workflow;
  }

  get isSingleWorkflowSelected() {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'workflow' does not exist on type '{}'.
    const {workflow, version} = this.state.filter;
    return workflow !== undefined && version !== undefined && version !== 'all';
  }

  get workflow() {
    const {groupedWorkflows, filter} = this.state;
    return getWorkflowByVersion(
      // @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message
      groupedWorkflows[filter.workflow],
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'version' does not exist on type '{}'.
      filter.version
    );
  }

  get workflowName() {
    const workflow = !isEmpty(this.workflow)
      ? this.workflow
      : // @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message
        this.state.groupedWorkflows[this.state.filter.workflow];

    return workflow ? workflow.name || workflow.bpmnProcessId : 'Workflow';
  }

  reset = () => {
    this.state = {...DEFAULT_STATE};

    this.filterObserveDisposer?.();
    this.locationReactionDisposer?.();
  };
}

export const filtersStore = new Filters();
