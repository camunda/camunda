/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, reaction, observe, autorun} from 'mobx';
import {fetchWorkflowInstancesStatistics} from 'modules/api/instances';
import {filters} from 'modules/stores/filters';
import {instancesDiagram} from 'modules/stores/instancesDiagram';
import {instances} from 'modules/stores/instances';
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
      () => instancesDiagram.state.diagramModel,
      () => {
        if (instancesDiagram.state.diagramModel !== null) {
          this.fetchWorkflowStatistics(filters.getFiltersPayload());
        }
      }
    );

    this.filterObserveDisposer = observe(filters.state, 'filter', (change) => {
      if (isEqual(filters.state.filter, change.oldValue)) {
        return;
      }

      if (isEmpty(filters.workflow)) {
        this.resetState();
      } else if (
        filters.state.filter.workflow === change.oldValue.workflow &&
        filters.state.filter.version === change.oldValue.version
      ) {
        this.fetchWorkflowStatistics(filters.getFiltersPayload());
      }
    });

    this.completedOperationsDisposer = autorun(() => {
      if (instances.state.instancesWithCompletedOperations.length > 0) {
        if (filters.isSingleWorkflowSelected) {
          this.fetchWorkflowStatistics(filters.getFiltersPayload());
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

export const workflowStatistics = new WorkflowStatistics();
