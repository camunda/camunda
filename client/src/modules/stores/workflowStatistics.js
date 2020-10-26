/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, reaction, observe, autorun} from 'mobx';
import {fetchWorkflowInstancesStatistics} from 'modules/api/instances';
import {filtersStore} from 'modules/stores/filters';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {instancesStore} from 'modules/stores/instances';
import {isEmpty, isEqual} from 'lodash';

const DEFAULT_STATE = {
  statistics: [],
  isLoading: false,
};

class WorkflowStatistics {
  state = {...DEFAULT_STATE};
  intervalId = null;
  disposer = null;
  diagramReactionDisposer = null;
  filterObserveDisposer = null;
  completedOperationsDisposer = null;

  init = () => {
    this.diagramReactionDisposer = reaction(
      () => instancesDiagramStore.state.diagramModel,
      () => {
        if (instancesDiagramStore.state.diagramModel !== null) {
          this.fetchWorkflowStatistics(filtersStore.getFiltersPayload());
        }
      }
    );

    this.filterObserveDisposer = observe(
      filtersStore.state,
      'filter',
      (change) => {
        if (isEqual(filtersStore.state.filter, change.oldValue)) {
          return;
        }

        if (isEmpty(filtersStore.workflow)) {
          this.resetState();
        } else if (
          filtersStore.state.filter.workflow === change.oldValue.workflow &&
          filtersStore.state.filter.version === change.oldValue.version
        ) {
          this.fetchWorkflowStatistics(filtersStore.getFiltersPayload());
        }
      }
    );

    this.completedOperationsDisposer = autorun(() => {
      if (instancesStore.state.instancesWithCompletedOperations.length > 0) {
        if (filtersStore.isSingleWorkflowSelected) {
          this.fetchWorkflowStatistics(filtersStore.getFiltersPayload());
        }
      }
    });
  };

  fetchWorkflowStatistics = async (payload) => {
    this.setWorkflowStatistics([]);
    this.startLoading();
    const response = await fetchWorkflowInstancesStatistics(payload);
    this.setWorkflowStatistics(response.statistics);
    this.stopLoading(response.statistics);
  };

  startLoading = () => {
    this.state.isLoading = true;
  };

  stopLoading = () => {
    this.state.isLoading = false;
  };

  setWorkflowStatistics = (statistics) => {
    this.state.statistics = statistics;
  };

  resetState = () => {
    this.state = {...DEFAULT_STATE};
  };

  reset = () => {
    this.resetState();

    this.diagramReactionDisposer?.(); // eslint-disable-line no-unused-expressions
    this.filterObserveDisposer?.(); // eslint-disable-line no-unused-expressions
    this.completedOperationsDisposer?.(); // eslint-disable-line no-unused-expressions
  };
}

decorate(WorkflowStatistics, {
  state: observable,
  startLoading: action,
  stopLoading: action,
  setWorkflowStatistics: action,
  resetState: action,
});

export const workflowStatisticsStore = new WorkflowStatistics();
