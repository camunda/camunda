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
export const licenseRequiredFields: string[] = [
  'validLicense',
  'licenseType',
  'isCommercial',
];
export const messageSubscriptionRequiredFields = [
  'messageSubscriptionKey',
  'processDefinitionId',
  'processDefinitionKey',
  'processInstanceKey',
  'elementId',
  'elementInstanceKey',
  'messageSubscriptionType',
  'lastUpdatedDate',
  'messageName',
  'correlationKey',
  'tenantId',
];
export const correlateMessageRequiredFields: string[] = [
  'tenantId',
  'messageKey',
  'processInstanceKey',
];
export const expectedMessageSubscription1 = {
  messageName: 'Message_143t419',
  correlationKey: '143419',
  tenantId: '<default>',
  elementId: 'Event_1idbbd5',
  processDefinitionId: 'messageCatchEvent1',
};
export const expectedMessageSubscription2 = {
  messageName: 'Message_3p6krla',
  correlationKey: '3234432',
  tenantId: '<default>',
  elementId: 'Event_1aspkyg',
  processDefinitionId: 'messageCatchEvent3',
};
export const expectedMessageSubscription3 = {
  messageName: 'Message_3tvi9o8',
  correlationKey: '3838383',
  tenantId: '<default>',
  elementId: 'Event_17u9bac',
  processDefinitionId: 'messageCatchEvent1',
};

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

export function PUBLISH_NEW_MESSAGE() {
  return {
    name: `msg-${Date.now()}`,
    correlationKey: `corr-${Math.random().toString(36).slice(2, 10)}`,
    messageId: `corr-${Math.random().toString(36).slice(2, 10)}`,
    timeToLive: 300000,
    variables: {foo: 'bar'},
  };
}

export const CORRELATE_MESSAGE = {
  name: expectedMessageSubscription3.messageName,
  correlationKey: expectedMessageSubscription3.correlationKey,
  variables: {foo: 'bar'},
};
