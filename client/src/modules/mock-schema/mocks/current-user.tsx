/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {User} from 'modules/types';

const currentUser: User = {
  username: 'demo',
  firstname: 'Demo',
  lastname: 'User',
  permissions: ['read', 'write'],
  __typename: 'User',
};

const currentRestrictedUser: User = {
  username: 'demo',
  firstname: 'Demo',
  lastname: 'User',
  permissions: ['read'],
  __typename: 'User',
};

const currentUserWithUnknownRole: User = {
  username: 'demo',
  firstname: 'Demo',
  lastname: 'User',
  // @ts-ignore
  permissions: ['unknown'],
  __typename: 'User',
};

const currentUserWithOutRole: User = {
  username: 'demo',
  firstname: 'Demo',
  lastname: 'User',
  // @ts-ignore
  permissions: [],
  __typename: 'User',
};

export {
  currentUser,
  currentRestrictedUser,
  currentUserWithOutRole,
  currentUserWithUnknownRole,
};
