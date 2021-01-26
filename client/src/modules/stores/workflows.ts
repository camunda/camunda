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
};

class Workflows {
  state: State = {
    workflows: [],
  };

  constructor() {
    makeAutoObservable(this, {
      fetch: false,
    });
  }

  fetch = async () => {
    try {
      const response = await fetchGroupedWorkflows();

      if (response.ok) {
        this.setWorkflows(await response.json());
      } else {
        logger.error('Failed to fetch workflows');
      }
    } catch (error) {
      logger.error('Failed to fetch workflows');
      logger.error(error);
    }
  };

  setWorkflows = (workflows: Workflow[]) => {
    this.state.workflows = workflows;
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

  get versionsByWorkflow() {
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
    this.state.workflows = [];
  };
}

const workflowsStore = new Workflows();

export {workflowsStore};
