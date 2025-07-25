/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const LOGIN_CREDENTIALS = {username: 'demo', password: 'demo'};

// Generate a simple random alphanumeric string for test isolation
export const generateUniqueId = () => {
  return Math.random().toString(36).substring(2, 10);
};

// Create unique user with optional custom ID
export const createUniqueUser = (customId?: string) => {
  const id = customId || generateUniqueId();
  return {
    username: `authtest${id}`,
    name: `Auth Test ${id}`,
    email: `auth${id}@test.com`,
    password: 'authtest123',
  };
};

// Create unique auth role with optional custom ID
export const createUniqueAuthRole = (customId?: string) => {
  const id = customId || generateUniqueId();
  return {
    id: `authrole${id}`,
    name: `Auth role ${id}`,
  };
};

// Create unique group with optional custom ID
export const createUniqueGroup = (customId?: string) => {
  const id = customId || generateUniqueId();
  return {
    groupId: `testgroup${id}`,
    name: `Test Group ${id}`,
    description: `Test group description ${id}`,
  };
};

// Create unique edited group data with optional custom ID
export const createEditedGroup = (customId?: string) => {
  const id = customId || generateUniqueId();
  return {
    name: `Edited Group ${id}`,
    description: `Edited group description ${id}`,
  };
};

export const createUserAuthorization = (authRole: {name: string}) => ({
  ownerType: 'Role',
  ownerId: authRole.name,
  resourceType: 'User',
  resourceId: '*',
  accessPermissions: ['update', 'create', 'read', 'delete'],
});

export const createApplicationAuthorization = (authRole: {name: string}) => ({
  ownerType: 'Role',
  ownerId: authRole.name,
  resourceType: 'Application',
  resourceId: '*',
  accessPermissions: ['access'],
});

// Generic function to create specific test data with shared ID
export const createTestData = (options: {
  user?: boolean;
  authRole?: boolean;
  userAuth?: boolean;
  applicationAuth?: boolean;
  group?: boolean;
  editedGroup?: boolean;
}) => {
  const {
    user = false,
    authRole = false,
    userAuth = false,
    applicationAuth = false,
    group = false,
    editedGroup = false,
  } = options;
  const sharedId = generateUniqueId();

  const result: {
    user?: ReturnType<typeof createUniqueUser>;
    authRole?: ReturnType<typeof createUniqueAuthRole>;
    userAuth?: ReturnType<typeof createUserAuthorization>;
    applicationAuth?: ReturnType<typeof createApplicationAuthorization>;
    group?: ReturnType<typeof createUniqueGroup>;
    editedGroup?: ReturnType<typeof createEditedGroup>;
    id: string;
  } = {id: sharedId};

  if (user) {
    result.user = createUniqueUser(sharedId);
  }

  if (authRole) {
    result.authRole = createUniqueAuthRole(sharedId);
  }

  if (group) {
    result.group = createUniqueGroup(sharedId);
  }

  if (editedGroup) {
    result.editedGroup = createEditedGroup(sharedId);
  }

  // Create authorizations only if authRole is also created
  if (userAuth && result.authRole) {
    result.userAuth = createUserAuthorization(result.authRole);
  }

  if (applicationAuth && result.authRole) {
    result.applicationAuth = createApplicationAuthorization(result.authRole);
  }

  return result;
};

export const NEW_AUTH_ROLE = {
  id: 'authrole',
  name: 'Auth role',
};
