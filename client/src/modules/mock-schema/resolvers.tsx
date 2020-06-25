/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {Resolvers, gql} from 'apollo-boost';
import {Task} from 'modules/types';

interface ResolverMap {
  [field: string]: (parent: any, args: any, context: any) => any;
}

interface AppResolvers extends Resolvers {
  Query: ResolverMap;
}

type IsAssigned = {
  [id: string]: boolean;
};

const isAssigned: IsAssigned = {};

const getAssignee = (task: Task) => {
  switch (isAssigned[task.id]) {
    case true:
      return {
        username: 'Demo',
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
  Query: {
    task(_, {id}, context) {
      const result: {tasks: Task[]} = context.client.readQuery({
        query: gql`
          query GetTasks {
            tasks(query: {}) {
              id
              name
              workflowName
              assignee
              creationTime
            }
          }
        `,
      });
      const task = result.tasks.find((task) => task.id === id);
      return {
        ...task,
        assignee: task ? getAssignee(task) : null,
        completionTime: null,
        variables:
          task?.name === 'registerPassenger'
            ? [{name: 'passengerId', value: '123645'}]
            : [],
      };
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
  },
};

export {resolvers};
