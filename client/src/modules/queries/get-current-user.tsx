/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {gql} from '@apollo/client';

import {User} from 'modules/types';
import {
  currentUser,
  currentRestrictedUser,
  currentUserWithUnknownRole,
  currentUserWithOutRole,
} from 'modules/mock-schema/mocks/current-user';

const GET_CURRENT_USER = gql`
  query GetCurrentUser {
    currentUser {
      userId
      displayName
      permissions
    }
  }
`;

const mockGetCurrentUser = {
  request: {
    query: GET_CURRENT_USER,
  },
  result: {
    data: {
      currentUser,
    },
  },
} as const;

const mockGetCurrentRestrictedUser = {
  request: {
    query: GET_CURRENT_USER,
  },
  result: {
    data: {
      currentUser: currentRestrictedUser,
    },
  },
} as const;

const mockGetCurrentUserWithUnknownRole = {
  request: {
    query: GET_CURRENT_USER,
  },
  result: {
    data: {
      currentUser: currentUserWithUnknownRole,
    },
  },
} as const;

const mockGetCurrentUserWithoutRole = {
  request: {
    query: GET_CURRENT_USER,
  },
  result: {
    data: {
      currentUser: currentUserWithOutRole,
    },
  },
} as const;

interface GetCurrentUser {
  currentUser: User;
}

export type {GetCurrentUser};
export {
  GET_CURRENT_USER,
  mockGetCurrentUser,
  mockGetCurrentRestrictedUser,
  mockGetCurrentUserWithUnknownRole,
  mockGetCurrentUserWithoutRole,
};
