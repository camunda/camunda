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

type TaskStates = {
  [id: string]: string;
};

const taskStates: TaskStates = {};

const getTaskState = (task: Task) => {
  if (taskStates[task.id]) {
    return taskStates[task.id];
  } else {
    return task.taskState;
  }
};

const resolvers: AppResolvers = {
  Task: {
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
    variables: () => {
      return [
        {name: 'myVar', value: '123'},
        {name: 'myVar2', value: 'true'},
      ];
    },
  },
  Mutation: {
    completeTask(_, {id, variables}, context) {
      taskStates[id] = TaskStates.Completed;
      const result: {task: Task} = context.client.readQuery({
        query: gql`
          query GetTask($id: String!) {
            task(id: $id) {
              id
              name
              workflowName
              assignee {
                firstname
                lastname
                username
              }
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
