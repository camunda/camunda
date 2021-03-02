/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeAutoObservable} from 'mobx';
import {fetchGroupedWorkflows} from 'modules/api/instances';
import {logger} from 'modules/logger';

type WorkflowVersion = {
  bpmnProcessId: string;
  id: string;
  name: string;
  version: number;
};

type Workflow = {
  bpmnProcessId: string;
  name: string;
  workflows: WorkflowVersion[];
};

type State = {
  workflows: Workflow[];
  status: 'initial' | 'fetching' | 'fetched' | 'fetch-error';
};

const INITIAL_STATE: State = {
  workflows: [],
  status: 'initial',
};

class Workflows {
  state: State = INITIAL_STATE;

  constructor() {
    makeAutoObservable(this, {
      fetchWorkflows: false,
    });
  }

  fetchWorkflows = async () => {
    this.startFetching();

    try {
      const response = await fetchGroupedWorkflows();

      if (response.ok) {
        this.handleFetchSuccess(await response.json());
      } else {
        this.handleFetchError();
      }
    } catch (error) {
      this.handleFetchError(error);
    }
  };

  startFetching = () => {
    this.state.status = 'fetching';
  };

  handleFetchError = (error?: Error) => {
    this.state.status = 'fetch-error';
    logger.error('Failed to fetch workflows');

    if (error !== undefined) {
      logger.error(error);
    }
  };

  handleFetchSuccess = (workflows: Workflow[]) => {
    this.state.workflows = workflows;
    this.state.status = 'fetched';
  };

  get workflows() {
    return this.state.workflows
      .map(({bpmnProcessId, name}) => ({
        value: bpmnProcessId,
        label: name ?? bpmnProcessId,
      }))
      .sort((workflow, nextWorkflow) => {
        const label = workflow.label.toUpperCase();
        const nextLabel = nextWorkflow.label.toUpperCase();

        if (label < nextLabel) {
          return -1;
        }
        if (label > nextLabel) {
          return 1;
        }

        return 0;
      });
  }

  get versionsByWorkflow(): {
    [bpmnProcessId: string]: WorkflowVersion[];
  } {
    return this.state.workflows.reduce<{
      [bpmnProcessId: string]: WorkflowVersion[];
    }>(
      (accumulator, {bpmnProcessId, workflows}) => ({
        ...accumulator,
        [bpmnProcessId]: workflows
          .slice()
          .sort(
            (workflow, nextWorkflow) => workflow.version - nextWorkflow.version
          ),
      }),
      {}
    );
  }

  reset = () => {
    this.state = INITIAL_STATE;
  };
}

const workflowsStore = new Workflows();

export {workflowsStore};
