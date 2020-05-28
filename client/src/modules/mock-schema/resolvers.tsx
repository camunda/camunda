/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const resolvers = {
  User: {
    username() {
      return 'demo';
    },
    firstname() {
      return 'Demo';
    },
    lastname() {
      return 'user';
    },
  },
  Query: {
    currentUser() {
      return {
        __typename: 'User',
      };
    },
  },
} as const;

export {resolvers};
