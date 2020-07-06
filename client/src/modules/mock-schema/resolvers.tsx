/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {Resolvers, gql} from 'apollo-boost';
import {Task} from 'modules/types';
import {TaskStates} from 'modules/constants/taskStates';

interface ResolverMap {
  [field: string]: (parent: any, args: any, context: any) => any;
}

interface AppResolvers extends Resolvers {
  Task: ResolverMap;
}

type IsAssigned = {
  [id: string]: boolean;
};

type TaskStates = {
  [id: string]: string;
};

const isAssigned: IsAssigned = {};
const taskStates: TaskStates = {};

const getTaskState = (task: Task) => {
  if (taskStates[task.id]) {
    return taskStates[task.id];
  } else {
    return task.taskState;
  }
};

const getAssignee = (task: Task) => {
  switch (isAssigned[task.id]) {
    case true:
      return {
        username: 'demo',
        firstname: 'Demo',
        lastname: 'User',
      };
    case false:
      return null;
    default:
      return task.assignee;
  }
};

const resolvers: AppResolvers = {
  Task: {
    assignee: (task) => {
      return getAssignee(task) || null;
    },
    taskState: (task) => {
      return getTaskState(task) || 'CREATED';
    },
    completionTime: (task) => {
      if (getTaskState(task) === TaskStates.Completed) {
        return task.creationTime;
      } else {
        return null;
      }
    },
  },
  Mutation: {
    claimTask(_, {id}) {
      isAssigned[id] = true;
      return {__typename: 'Task', id};
    },
    unclaimTask(_, {id}) {
      isAssigned[id] = false;
      return {__typename: 'Task', id};
    },
    completeTask(_, {id, variables}, context) {
      taskStates[id] = TaskStates.Completed;
      const result: {task: Task} = context.client.readQuery({
        query: gql`
          query GetTask($id: String!) {
            task(id: $id) {
              id
              name
              workflowName
              assignee
              creationTime
            }
          }
        `,
        variables: {id},
      });

      const {task} = result;
      return {
        ...task,
        __typename: 'Task',
        variables,
        completionTime: task?.creationTime,
        taskState: TaskStates.Completed,
      };
    },
  },
};

export {resolvers};
