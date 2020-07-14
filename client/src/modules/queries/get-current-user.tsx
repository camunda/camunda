/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {gql} from 'apollo-boost';

import {User} from 'modules/types';
import {currentUser} from 'modules/mock-schema/constants/currentUser';

const GET_CURRENT_USER = gql`
  query GetCurrentUser {
    currentUser {
      firstname
      lastname
      username
      canLogout
    }
  }
`;

const mockGetCurrentUser = {
  request: {
    query: GET_CURRENT_USER,
  },
  result: {
    data: {
      currentUser: {
        ...currentUser,
        canLogout: true,
      },
    },
  },
} as const;

const mockSSOGetCurrentUser = {
  request: {
    query: GET_CURRENT_USER,
  },
  result: {
    data: {
      currentUser: {
        ...currentUser,
        canLogout: false,
      },
    },
  },
} as const;

interface GetCurrentUser {
  currentUser: User;
}

export type {GetCurrentUser};
export {GET_CURRENT_USER, mockGetCurrentUser, mockSSOGetCurrentUser};
