/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {generateUniqueId} from '../constants';

export const groupRequiredFields: string[] = ['groupId', 'name', 'description'];
export const mappingRuleRequiredFields: string[] = [
  'claimName',
  'claimValue',
  'name',
  'mappingRuleId',
];
export const roleRequiredFields: string[] = ['roleId', 'name', 'description'];

export function CREATE_NEW_GROUP() {
  return {
    groupId: 'group' + generateUniqueId(),
    name: 'new group' + generateUniqueId(),
    description: 'Test group description',
  };
}

export function CREATE_NEW_MAPPING_RULE() {
  const uniqueName = generateUniqueId();
  return {
    claimName: 'claimName' + uniqueName,
    claimValue: 'claimValue' + uniqueName,
    name: 'name' + uniqueName,
    mappingRuleId: 'rule' + uniqueName,
  };
}

export function CREATE_NEW_ROLE() {
  const uid = generateUniqueId();
  return {
    roleId: `role-${uid}`,
    name: `Test Role ${uid}`,
    description: 'E2E test role',
  };
}
