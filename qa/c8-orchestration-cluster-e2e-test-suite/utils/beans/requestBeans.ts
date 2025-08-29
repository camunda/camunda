/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {generateUniqueId} from '../constants';
import * as fs from 'node:fs';
import {createMappingRule, mappingRuleIdFromState} from '../requestHelpers';
import {APIRequestContext} from 'playwright-core';

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
export const documentRequiredFields = [
  'camunda.document.type',
  'storeId',
  'documentId',
  'contentHash',
  'metadata',
];
export const multipleDocumentsRequiredFields = [
  'createdDocuments',
  'failedDocuments',
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

export function CREATE_TXT_DOCUMENT_REQUEST() {
  const form = new FormData();
  form.append(
    'file',
    new Blob([fs.readFileSync('./resources/helloworld.txt')], {
      type: 'text/plain',
    }),
    'helloworld.txt',
  );
  return form;
}

export function CREATE_DOC_INVALID_REQUEST() {
  const form = new FormData();
  form.append('metadata', '{}');
  return form;
}

export function CREATE_TXT_DOC_RESPONSE_BODY(name: string, size: number) {
  return {
    'camunda.document.type': 'camunda',
    storeId: 'in-memory',
    metadata: {
      contentType: 'text/plain',
      fileName: `${name}.txt`,
      size: size,
      customProperties: {},
    },
  };
}

export function CREATE_TXT_DOC_RESPONSE_WITH_METADATA(
  name: string,
  size: number,
) {
  return {
    'camunda.document.type': 'camunda',
    storeId: 'in-memory',
    metadata: {
      contentType: 'text/plain',
      fileName: `${name}.txt`,
      size: size,
      processDefinitionId: name,
      processInstanceKey: '123456',
      customProperties: {foo: 'bar'},
    },
  };
}

export function CREATE_ON_FLY_DOCUMENT_REQUEST_BODY_WITH_METADATA(
  name: string,
) {
  const form = new FormData();
  form.append(
    'file',
    new File([`Hello World ${name}!`], `${name}.txt`, {
      type: 'text/ plain',
    }),
  );
  form.append(
    'metadata',
    new Blob(
      [
        JSON.stringify({
          contentType: 'text/plain',
          fileName: `${name}.txt`,
          processDefinitionId: name,
          processInstanceKey: '123456',
          customProperties: {foo: 'bar'},
        }),
      ],
      {
        type: 'application/json',
      },
    ),
  );
  return form;
}

export function CREATE_ON_FLY_MULTIPLE_DOCUMENTS_REQUEST_BODY(
  name: string,
  numberOfDocs: number = 1,
) {
  const form = new FormData();
  for (let i = 1; i <= numberOfDocs; i++) {
    form.append(
      'files',
      new File([`Hello World ${name + i}!`], `${name}${i}.txt`, {
        type: 'text/plain',
      }),
    );
  }
  return form;
}

export const CREATE_DOCUMENT_LINK_REQUEST = {
  timeToLive: 60000,
};

export function CREATE_MAPPING_EXPECTED_BODY_USING_GROUP(
  groupId: string,
  state: Record<string, unknown>,
  nth: number = 1,
) {
  return {
    claimName: state[`${groupId}claimName${nth}`] as string,
    claimValue: state[`${groupId}claimValue${nth}`] as string,
    name: state[`${groupId}name${nth}`] as string,
    mappingRuleId: state[`${groupId}mappingRule${nth}`] as string,
  };
}

export function CREATE_MAPPING_EXPECTED_BODY(
  key: string,
  state: Record<string, unknown>,
) {
  return {
    claimName: state[`claimName${key}`] as string,
    claimValue: state[`claimValue${key}`] as string,
    name: state[`name${key}`] as string,
    mappingRuleId: state[`mappingRuleId${key}`] as string,
  };
}

export function CREATE_GROUP_ROLE_EXPECTED_BODY_USING_GROUP(
  groupId: string,
  state: Record<string, unknown>,
  nth: number = 1,
) {
  return {
    name: state[`${groupId}name${nth}`] as string,
    roleId: state[`${groupId}roleId${nth}`] as string,
    description: state[`${groupId}description${nth}`] as string,
  };
}

export function CREATE_GROUP_USERS_EXPECTED_BODY_USING_GROUP(
  groupId: string,
  state: Record<string, unknown>,
  nth: number = 1,
) {
  return {
    username: state[`${groupId}user${nth}`] as string,
  };
}

export async function mappingRuleBundle(
  request: APIRequestContext,
  state: Record<string, unknown>,
) {
  const mappingRuleKey = 'mappingRuleId' + generateUniqueId();
  await createMappingRule(request, state, mappingRuleKey);
  return {
    mappingRuleKey: 'mappingRuleId' + generateUniqueId(),
    mappingRuleId: mappingRuleIdFromState(mappingRuleKey, state),
    responseBody: CREATE_MAPPING_EXPECTED_BODY(mappingRuleKey, state),
  };
}
