/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  makeAutoObservable,
  reaction,
  observe,
  IReactionDisposer,
  Lambda,
} from 'mobx';
import {fetchWorkflowInstancesStatistics} from 'modules/api/instances';
import {filtersStore} from 'modules/stores/filters';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';
import {instancesStore} from 'modules/stores/instances';
import {isEmpty, isEqual} from 'lodash';

type NodeStatistics = {
  active: number;
  activityId: string;
  canceled: number;
  completed: number;
  incidents: number;
};
type State = {
  statistics: NodeStatistics[];
  isLoading: boolean;
};

const DEFAULT_STATE: State = {
  statistics: [],
  isLoading: false,
};

class WorkflowStatistics {
  state: State = {...DEFAULT_STATE};
  diagramReactionDisposer: null | IReactionDisposer = null;
  filterObserveDisposer: null | Lambda = null;

  constructor() {
    makeAutoObservable(this);
  }

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
          // @ts-expect-error ts-migrate(2339) FIXME: Property 'workflow' does not exist on type '{}'.
          filtersStore.state.filter.workflow === change.oldValue.workflow &&
          // @ts-expect-error ts-migrate(2339) FIXME: Property 'version' does not exist on type '{}'.
          filtersStore.state.filter.version === change.oldValue.version
        ) {
          this.fetchWorkflowStatistics(filtersStore.getFiltersPayload());
        }
      }
    );

    instancesStore.addCompletedOperationsHandler(() => {
      if (filtersStore.isSingleWorkflowSelected) {
        this.fetchWorkflowStatistics(filtersStore.getFiltersPayload());
      }
    });
  };

  fetchWorkflowStatistics = async (payload: any) => {
    this.setWorkflowStatistics([]);
    this.startLoading();
    const response = await fetchWorkflowInstancesStatistics(payload);
    this.setWorkflowStatistics(response.statistics);
    this.stopLoading();
  };

  startLoading = () => {
    this.state.isLoading = true;
  };

  stopLoading = () => {
    this.state.isLoading = false;
  };

  setWorkflowStatistics = (statistics: NodeStatistics[]) => {
    this.state.statistics = statistics;
  };

  resetState = () => {
    this.state = {...DEFAULT_STATE};
  };

  reset = () => {
    this.resetState();

    this.diagramReactionDisposer?.();
    this.filterObserveDisposer?.();
  };
}

export const workflowStatisticsStore = new WorkflowStatistics();
