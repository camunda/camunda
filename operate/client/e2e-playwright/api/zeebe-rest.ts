/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ZeebeRestClient} from '@camunda8/sdk/dist/zeebe';
import {camunda8} from './camunda8';

interface TaskChangeSet {
  dueDate?: Date | string;
  followUpDate?: Date | string;
  candidateUsers?: string[];
  candidateGroups?: string[];
}

type AssignTaskParams = {
  userTaskKey: string;
  assignee: string;
  allowOverride?: boolean;
  action: string;
};

type CompleteUserTaskParams = {
  userTaskKey: string;
  variables?: Record<string, unknown>;
  action?: string;
};

type UpdateTaskParams = {
  userTaskKey: string;
  changeset: TaskChangeSet;
};

type RemoveAssigneeParams = {
  userTaskKey: string;
};

class ZeebeRestApi {
  zeebe: ZeebeRestClient;

  constructor() {
    this.zeebe = camunda8.getZeebeRestClient();
  }

  getTopology() {
    return this.zeebe.getTopology();
  }

  assignTask({userTaskKey, assignee, allowOverride, action}: AssignTaskParams) {
    return this.zeebe.assignTask({
      userTaskKey,
      assignee,
      allowOverride,
      action,
    });
  }

  completeUserTask({userTaskKey, variables, action}: CompleteUserTaskParams) {
    return this.zeebe.completeUserTask({userTaskKey, variables, action});
  }

  updateTask({userTaskKey, changeset}: UpdateTaskParams) {
    return this.zeebe.updateTask({
      userTaskKey,
      changeset,
    });
  }

  removeAssignee({userTaskKey}: RemoveAssigneeParams) {
    return this.zeebe.removeAssignee({userTaskKey});
  }
}

const zeebeRestApi = new ZeebeRestApi();

export {zeebeRestApi};
