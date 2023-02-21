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
  currentUserWithC8Links,
} from 'modules/mock-schema/mocks/current-user';

const GET_CURRENT_USER = gql`
  query GetCurrentUser {
    currentUser {
      userId
      displayName
      permissions
      salesPlanType
      roles
      c8Links {
        name
        link
      }
    }
  }
`;

type GetCurrentUser = {
  currentUser: User;
};

const mockGetCurrentUser: GetCurrentUser = {
  currentUser,
};

const mockGetCurrentUserWithC8Links: GetCurrentUser = {
  currentUser: currentUserWithC8Links,
};

const mockGetCurrentRestrictedUser: GetCurrentUser = {
  currentUser: currentRestrictedUser,
};

const mockGetCurrentUserWithUnknownRole: GetCurrentUser = {
  currentUser: currentUserWithUnknownRole,
};

const mockGetCurrentUserWithoutRole: GetCurrentUser = {
  currentUser: currentUserWithOutRole,
};

const mockGetCurrentUserWithCustomSalesPlanType = (
  salesPlanType: User['salesPlanType'],
): GetCurrentUser => ({
  currentUser: {...currentUser, salesPlanType},
});

export type {GetCurrentUser};
export {
  GET_CURRENT_USER,
  mockGetCurrentUser,
  mockGetCurrentRestrictedUser,
  mockGetCurrentUserWithUnknownRole,
  mockGetCurrentUserWithoutRole,
  mockGetCurrentUserWithC8Links,
  mockGetCurrentUserWithCustomSalesPlanType,
};
