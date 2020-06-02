/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Resolvers} from 'apollo-boost';

import {currentUser} from './mocks/currentUser';
import {tasks} from './mocks/tasks';

interface ResolverMap {
  [field: string]: (parent: any, args: any) => any;
}

interface AppResolvers extends Resolvers {
  Query: ResolverMap;
  User: ResolverMap;
  Task: ResolverMap;
}

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
      return tasks[index].assignee;
    },
    variables({index}) {
      return tasks[index].variables;
    },
    taskState({index}) {
      return tasks[index].taskState;
    },
  },
  User: {
    username() {
      return currentUser.username;
    },
    firstname() {
      return currentUser.firstname;
    },
    lastname() {
      return currentUser.lastname;
    },
  },
  Query: {
    currentUser() {
      return {
        __typename: 'User',
      };
    },
    tasks() {
      return [...new Array(2)].map((_, index) => ({
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
};

export {resolvers};
