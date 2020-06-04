/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {gql} from 'apollo-boost';

import {User} from 'modules/types';

const GET_HEADER_USER = gql`
  query GetHeaderUser {
    currentUser {
      firstname
      lastname
    }
  }
`;

const mockGetHeaderUser = {
  request: {
    query: GET_HEADER_USER,
  },
  result: {
    data: {
      currentUser: {
        firstname: 'Demo',
        lastname: 'user',
      },
    },
  },
} as const;

interface GetHeaderUser {
  currentUser: {
    firstname: User['firstname'];
    lastname: User['lastname'];
  };
}

export type {GetHeaderUser};
export {GET_HEADER_USER, mockGetHeaderUser};
