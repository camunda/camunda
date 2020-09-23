/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, reaction, observe} from 'mobx';
import {fetchWorkflowInstancesStatistics} from 'modules/api/instances';
import {filters} from 'modules/stores/filters';
import {instancesDiagram} from 'modules/stores/instancesDiagram';
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

    if (this.diagramReactionDisposer !== null) {
      this.diagramReactionDisposer();
    }
    if (this.filterObserveDisposer !== null) {
      this.filterObserveDisposer();
    }
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
