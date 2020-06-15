/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {Resolvers} from 'apollo-boost';

import {tasks} from './mocks/tasks';
interface ResolverMap {
  [field: string]: (parent: any, args: any) => any;
}

interface AppResolvers extends Resolvers {
  Query: ResolverMap;
  Task: ResolverMap;
}

type IsAssigned = {
  [key: number]: boolean;
};

const isAssigned: IsAssigned = {};

const getAssignee = (key: number) => {
  switch (isAssigned[key]) {
    case true:
      return {
        username: 'Demo',
        firstname: 'Demo',
        lastname: 'User',
      };
    case false:
      return null;
    default:
      return tasks[key].assignee;
  }
};

const resolvers: AppResolvers = {
  Task: {
    key({index}) {
      return tasks[index].key;
    },
    name({index}) {
      return tasks[index].name;
    },
    workflowName({index}) {
      return tasks[index].workflowName;
    },
    creationTime({index}) {
      return tasks[index].creationTime;
    },
    completionTime({index}) {
      return tasks[index].completionTime;
    },
    assignee({index}) {
      return getAssignee(index);
    },
    variables({index}) {
      return tasks[index].variables;
    },
    taskState({index}) {
      return tasks[index].taskState;
    },
  },
  Query: {
    tasks() {
      return [...new Array(3)].map((_, index) => ({
        __typename: 'Task',
        index,
      }));
    },
    task(_, {key}) {
      return {
        __typename: 'Task',
        index: key,
      };
    },
  },
  Mutation: {
    claimTask(_, {key}) {
      isAssigned[key] = true;
      return {__typename: 'Task', index: key};
    },
    unclaimTask(_, {key}) {
      isAssigned[key] = false;
      return {__typename: 'Task', index: key};
    },
  },
};

export {resolvers};
