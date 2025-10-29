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

export const defaultAssertionOptions = {
  intervals: [5_000, 10_000, 15_000],
  timeout: 30_000,
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

// Create unique tenant group data with optional custom ID
export const createUniqueTenant = (customId?: string) => {
  const id = customId || generateUniqueId();
  return {
    tenantId: `tenant${id}`,
    name: `Test Tenant ${id}`,
    description: `Test tenant description ${id}`,
  };
};

// Create unique mapping rule with optional custom ID
export const createUniqueMappingRule = (customId?: string) => {
  const id = customId || generateUniqueId();
  return {
    id: `mapping${id}`,
    name: `Test Mapping Rule ${id}`,
    claimName: `claim${id}`,
    claimValue: `value${id}`,
  };
};

// Create unique edited mapping rule data with optional custom ID
export const createEditedMappingRule = (customId?: string) => {
  const id = customId || generateUniqueId();
  return {
    name: `Edited Mapping Rule ${id}`,
    claimName: `edited-claim${id}`,
    claimValue: `edited-value${id}`,
  };
};

// Generic function to create specific test data with shared ID

export const createUserAuthorization = (authRole: {name: string}) => ({
  ownerType: 'Role',
  ownerId: authRole.name,
  resourceType: 'User',
  resourceId: '*',
  accessPermissions: ['update', 'create', 'read', 'delete'],
});

export const createComponentAuthorization = (authRole: {name: string}) => ({
  ownerType: 'Role',
  ownerId: authRole.name,
  resourceType: 'Component',
  resourceId: '*',
  accessPermissions: ['access'],
});

// Generic function to create specific test data with shared ID
export const createTestData = (options: {
  user?: boolean;
  authRole?: boolean;
  userAuth?: boolean;
  componentAuth?: boolean;
  group?: boolean;
  editedGroup?: boolean;
  tenant?: boolean;
  mappingRule?: boolean;
  editedMappingRule?: boolean;
}) => {
  const {
    user = false,
    authRole = false,
    userAuth = false,
    componentAuth = false,
    group = false,
    editedGroup = false,
    tenant = false,
    mappingRule = false,
    editedMappingRule = false,
  } = options;
  const sharedId = generateUniqueId();

  const result: {
    user?: ReturnType<typeof createUniqueUser>;
    authRole?: ReturnType<typeof createUniqueAuthRole>;
    userAuth?: ReturnType<typeof createUserAuthorization>;
    componentAuth?: ReturnType<typeof createComponentAuthorization>;
    group?: ReturnType<typeof createUniqueGroup>;
    editedGroup?: ReturnType<typeof createEditedGroup>;
    tenant?: ReturnType<typeof createUniqueTenant>;
    mappingRule?: ReturnType<typeof createUniqueMappingRule>;
    editedMappingRule?: ReturnType<typeof createEditedMappingRule>;
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

  if (tenant) {
    result.tenant = createUniqueTenant(sharedId);
  }

  if (mappingRule) {
    result.mappingRule = createUniqueMappingRule(sharedId);
  }

  if (editedMappingRule) {
    result.editedMappingRule = createEditedMappingRule(sharedId);
  }

  // Create authorizations only if authRole is also created
  if (userAuth && result.authRole) {
    result.userAuth = createUserAuthorization(result.authRole);
  }

  if (componentAuth && result.authRole) {
    result.componentAuth = createComponentAuthorization(result.authRole);
  }

  return result;
};

export const NEW_AUTH_ROLE = {
  id: 'authrole',
  name: 'Auth role',
};
