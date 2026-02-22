/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {generateUniqueId} from '../constants';
import * as fs from 'node:fs';
import {readFileSync} from 'node:fs';
import {
  createMappingRule,
  mappingRuleClaimNameFromState,
  mappingRuleClaimValueFromState,
  mappingRuleIdFromKey,
  mappingRuleIdFromState,
  mappingRuleNameFromState,
  roleDescriptionFromState,
  roleIdValueUsingKey,
  roleNameFromState,
  userFromState,
} from '@requestHelpers';
import {APIRequestContext} from 'playwright-core';
import {DecisionDeployment} from '@camunda8/sdk/dist/c8/lib/C8Dto';

export const jobResponseFields = [
  'type',
  'processDefinitionId',
  'processDefinitionVersion',
  'elementId',
  'customHeaders',
  'worker',
  'retries',
  'deadline',
  'variables',
  'tenantId',
  'jobKey',
  'processInstanceKey',
  'processDefinitionKey',
  'elementInstanceKey',
  'kind',
  'listenerEventType',
];
export const jobSearchPageResponseRequiredFields = ['totalItems'];
export const userTaskSearchPageResponseRequiredFields = ['totalItems'];
export const jobSearchItemResponseFields = [
  'customHeaders',
  'elementInstanceKey',
  'hasFailedWithRetriesLeft',
  'jobKey',
  'kind',
  'listenerEventType',
  'processDefinitionId',
  'processDefinitionKey',
  'processInstanceKey',
  'retries',
  'state',
  'tenantId',
  'type',
  'worker',
];
export const userTaskSearchItemResponseFields = [
  'name',
  'state',
  'assignee',
  'elementId',
  'candidateGroups',
  'candidateUsers',
  'processDefinitionId',
  'creationDate',
  'completionDate',
  'followUpDate',
  'dueDate',
  'tenantId',
  'externalFormReference',
  'processDefinitionVersion',
  'priority',
  'userTaskKey',
  'elementInstanceKey',
  'processName',
  'processDefinitionKey',
  'processInstanceKey',
  'formKey',
];
export const clusterTopologyResponseFields = [
  'brokers',
  'clusterSize',
  'partitionsCount',
  'replicationFactor',
  'gatewayVersion',
  'lastCompletedChangeId',
];
export const brokerResponseFields = [
  'nodeId',
  'host',
  'port',
  'partitions',
  'version',
];
export const partitionsResponseFields = ['partitionId', 'role', 'health'];
export const groupRequiredFields: string[] = ['groupId', 'name', 'description'];
export const tenantRequiredFields: string[] = [
  'tenantId',
  'name',
  'description',
];
export const mappingRuleRequiredFields: string[] = [
  'claimName',
  'claimValue',
  'name',
  'mappingRuleId',
];
export const roleRequiredFields: string[] = ['roleId', 'name', 'description'];
export const authorizedComponentRequiredFields: string[] = ['authorizationKey'];
export const authorizationRequiredFields: string[] = [
  'ownerId',
  'ownerType',
  'resourceType',
  'resourceId',
  'permissionTypes',
  'authorizationKey',
];
export const authorizationRequiredFieldsWithoutPermissionTypes: string[] = [
  'ownerId',
  'ownerType',
  'resourceType',
  'resourceId',
  'authorizationKey',
];
export const userRequiredFields: string[] = ['username', 'name', 'email'];
export const decisionDefinitionRequiredFields: string[] = [
  'decisionDefinitionId',
  'name',
  'version',
  'decisionRequirementsId',
  'tenantId',
  'decisionDefinitionKey',
  'decisionRequirementsKey',
];
export const decisionRequirementRequiredFields: string[] = [
  'decisionRequirementsId',
  'version',
  'decisionRequirementsName',
  'tenantId',
  'decisionRequirementsKey',
  'resourceName',
];
export const decisionInstanceRequiredFields: string[] = [
  'decisionEvaluationInstanceKey',
  'state',
  'evaluationDate',
  'decisionDefinitionId',
  'decisionDefinitionName',
  'decisionDefinitionVersion',
  'decisionDefinitionType',
  'result',
  'tenantId',
  'decisionEvaluationKey',
  'processDefinitionKey',
  'processInstanceKey',
  'decisionDefinitionKey',
  'elementInstanceKey',
  'rootDecisionDefinitionKey',
];
export const getDecisionInstanceResponseRequiredFields: string[] = [
  'decisionEvaluationInstanceKey',
  'state',
  'evaluationDate',
  'decisionDefinitionId',
  'decisionDefinitionName',
  'decisionDefinitionVersion',
  'decisionDefinitionType',
  'result',
  'tenantId',
  'decisionEvaluationKey',
  'processDefinitionKey',
  'processInstanceKey',
  'decisionDefinitionKey',
  'elementInstanceKey',
  'evaluatedInputs',
  'matchedRules',
  'rootDecisionDefinitionKey',
];
export const evaluateDecisionRequiredFields: string[] = [
  'decisionDefinitionId',
  'decisionDefinitionName',
  'decisionDefinitionVersion',
  'decisionRequirementsId',
  'output',
  'failedDecisionDefinitionId',
  'failureMessage',
  'tenantId',
  'decisionDefinitionKey',
  'decisionRequirementsKey',
  'decisionInstanceKey',
  'decisionEvaluationKey',
  'evaluatedDecisions',
];
export const authenticationRequiredFields: string[] = [
  'username',
  'displayName',
  'email',
  'authorizedComponents',
  'tenants',
  'groups',
  'roles',
  'c8Links',
  'canLogout',
];
export const licenseRequiredFields: string[] = [
  'validLicense',
  'licenseType',
  'isCommercial',
];
export const messageSubscriptionRequiredFields = [
  'messageSubscriptionKey',
  'processDefinitionId',
  'processInstanceKey',
  'elementId',
  'elementInstanceKey',
  'messageSubscriptionState',
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
export const correlatedMessageSubscriptionRequiredFields: string[] = [
  'correlationKey',
  'correlationTime',
  'elementId',
  'elementInstanceKey',
  'messageKey',
  'messageName',
  'partitionId',
  'processDefinitionId',
  'processInstanceKey',
  'subscriptionKey',
  'tenantId',
];
export const usageMetricsGetResponseRequiredFields: string[] = [
  'activeTenants',
  'tenants',
  'processInstances',
  'decisionInstances',
  'assignees',
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

export const expectedMessageSubscription1DoubleProcess = {
  messageName: 'Message_256spvh',
  correlationKey: '9324715',
  tenantId: '<default>',
  elementId: 'Event_Up',
  processDefinitionId: 'Process_double_message_catch',
};

export const expectedMessageSubscription2DoubleProcess = {
  messageName: 'Message_1tu3d5h',
  correlationKey: '191919',
  tenantId: '<default>',
  elementId: 'Event_down',
  processDefinitionId: 'Process_double_message_catch',
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

export function CREATE_NEW_USER() {
  const uid = generateUniqueId();
  return {
    username: `username-${uid}`,
    name: `name-${uid}`,
    email: `email-${uid}@example.com`,
    password: `password-${uid}`,
  };
}

export function UPDATE_USER() {
  const uid = generateUniqueId();
  return {
    name: `updated-name-${uid}`,
    email: `updated-email-${uid}@example.com`,
  };
}

export function UPDATE_ROLE() {
  const uid = generateUniqueId();
  return {
    name: `role-updated-${uid}`,
    description: `Updated description-${uid}`,
  };
}

export function CREATE_NEW_TENANT() {
  const uid = generateUniqueId();
  return {
    tenantId: `tenant-${uid}`,
    name: `Test Tenant ${uid}`,
    description: `E2E test tenant ${uid}`,
  };
}

export function UPDATE_TENANT() {
  const uid = generateUniqueId();
  return {
    name: `tenant-updated-${uid}`,
    description: `Updated description-${uid}`,
  };
}

export function TENANT_EXPECTED_BODY(
  name: string,
  tenantId: string,
  description: string,
) {
  return {
    name: name,
    tenantId: tenantId,
    description: description,
  };
}

export function PUBLISH_NEW_MESSAGE() {
  const uid = generateUniqueId();
  return {
    name: `msg-${uid}`,
    correlationKey: `corr-${uid}`,
    messageId: `corr-${uid}`,
    timeToLive: 300000,
    variables: {foo: 'bar'},
  };
}

export const CORRELATE_MESSAGE = {
  name: expectedMessageSubscription3.messageName,
  correlationKey: expectedMessageSubscription3.correlationKey,
  variables: {foo: 'bar'},
};

export const CORRELATE_MESSAGE1 = {
  name: expectedMessageSubscription1.messageName,
  correlationKey: expectedMessageSubscription1.correlationKey,
  variables: {foo: 'bar'},
};

export const CORRELATE_MESSAGE2 = {
  name: expectedMessageSubscription2.messageName,
  correlationKey: expectedMessageSubscription2.correlationKey,
  variables: {foo: 'bar'},
};

export const CORRELATE_MESSAGE_DOUBLE_1 = {
  name: expectedMessageSubscription1DoubleProcess.messageName,
  correlationKey: expectedMessageSubscription1DoubleProcess.correlationKey,
};

export const CORRELATE_MESSAGE_DOUBLE_2 = {
  name: expectedMessageSubscription2DoubleProcess.messageName,
  correlationKey: expectedMessageSubscription2DoubleProcess.correlationKey,
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
      expiresAt: null,
      size: size,
      processDefinitionId: null,
      processInstanceKey: null,
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
      expiresAt: null,
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

export function EVALUATE_DECISION_EXPECTED_BODY(
  decision: DecisionDeployment,
  output: string,
) {
  return {
    decisionDefinitionId: decision.decisionDefinitionId,
    decisionDefinitionName: decision.name,
    decisionDefinitionVersion: decision.version,
    decisionRequirementsId: decision.decisionRequirementsId,
    output: output,
    failedDecisionDefinitionId: null,
    failureMessage: null,
    tenantId: '<default>',
    decisionDefinitionKey: decision.decisionDefinitionKey,
    decisionRequirementsKey: decision.decisionRequirementsKey,
  };
}

export function EVALUATED_DECISION_EXPECTED_BODY(
  decision: DecisionDeployment,
  matchedRuleOptions: {
    output: string;
    ruleId?: string;
    outputId?: string;
    outputName?: string;
    outputValue?: string;
    input: {
      inputId: string;
      inputName: string;
      inputValue: string;
    }[];
    ruleIndex?: number;
  },
  emptyResults: boolean = false,
) {
  return {
    decisionDefinitionId: decision.decisionDefinitionId,
    decisionDefinitionName: decision.name,
    decisionDefinitionVersion: decision.version,
    output: matchedRuleOptions.output,
    tenantId: '<default>',
    matchedRules: emptyResults
      ? []
      : [
          {
            ruleId: matchedRuleOptions.ruleId,
            ruleIndex: matchedRuleOptions.ruleIndex,
            evaluatedOutputs: [
              {
                outputId: matchedRuleOptions.outputId,
                outputName: matchedRuleOptions.outputName,
                outputValue: matchedRuleOptions.outputValue,
                ruleId: null,
                ruleIndex: null,
              },
            ],
          },
        ],
    evaluatedInputs: matchedRuleOptions.input,
    decisionDefinitionKey: decision.decisionDefinitionKey,
  };
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

export function MAPPING_RULE_EXPECTED_BODY_USING_STATE(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
) {
  return {
    claimName: mappingRuleClaimNameFromState(key, state, nth) as string,
    claimValue: mappingRuleClaimValueFromState(key, state, nth) as string,
    name: mappingRuleNameFromState(key, state, nth) as string,
    mappingRuleId: mappingRuleIdFromState(key, state, nth) as string,
  };
}

export function MAPPING_RULE_EXPECTED_BODY_USING_KEY(
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

export function ROLE_EXPECTED_BODY(
  key: string,
  state: Record<string, unknown>,
  nth: number = 1,
) {
  return {
    name: roleNameFromState(key, state, nth),
    roleId: roleIdValueUsingKey(key, state, nth),
    description: roleDescriptionFromState(key, state, nth),
  };
}

export function CREATE_GROUP_USERS_EXPECTED_BODY_USING_GROUP(
  groupId: string,
  state: Record<string, unknown>,
  nth: number = 1,
) {
  return {
    username: userFromState(groupId, state, nth),
  };
}

export function GROUPS_EXPECTED_BODY(groupId: string) {
  return {
    groupId: groupId,
  };
}

export function CREATE_COMPONENT_AUTHORIZATION(
  ownerType: 'ROLE' | 'USER' | 'GROUP',
  ownerId: string,
) {
  return {
    ownerType: ownerType,
    ownerId: ownerId,
    resourceType: 'COMPONENT',
    resourceId: '*',
    permissionTypes: ['ACCESS'],
  };
}

export function CREATE_CUSTOM_AUTHORIZATION_BODY(
  ownerId: string,
  ownerType:
    | 'USER'
    | 'CLIENT'
    | 'ROLE'
    | 'GROUP'
    | 'MAPPING_RULE'
    | 'WRONG_VALUE_FOR_TEST',
  resourceId: string,
  resourceType:
    | 'AUDIT_LOG'
    | 'AUTHORIZATION'
    | 'MAPPING_RULE'
    | 'MESSAGE'
    | 'BATCH'
    | 'COMPONENT'
    | 'SYSTEM'
    | 'TENANT'
    | 'RESOURCE'
    | 'PROCESS_DEFINITION'
    | 'DECISION_REQUIREMENTS_DEFINITION'
    | 'DECISION_DEFINITION'
    | 'GROUP'
    | 'USER'
    | 'ROLE'
    | 'DOCUMENT'
    | 'USER_TASK'
    | 'WRONG_VALUE_FOR_TEST',
  permissionTypes: string[],
) {
  return {
    ownerId: ownerId,
    ownerType: ownerType,
    resourceType: resourceType,
    resourceId: resourceId,
    permissionTypes: permissionTypes,
  };
}

export function GET_CURRENT_USER_EXPECTED_BODY(
  username: string,
  name: string,
  email: string,
  assignedResources: {
    authorizedComponents?: string[];
    tenants?: {name: string; tenantId: string; description: string}[];
    groups?: string[];
    roles?: string[];
  } = {
    authorizedComponents: [],
    tenants: [],
    groups: [],
    roles: [],
  },
) {
  return {
    username: username,
    displayName: name,
    email: email,
    authorizedComponents: assignedResources.authorizedComponents ?? [],
    tenants: assignedResources.tenants ?? [],
    groups: assignedResources.groups ?? [],
    roles: assignedResources.roles ?? [],
    c8Links: {},
    canLogout: true,
  };
}

export async function mappingRuleBundle(
  request: APIRequestContext,
  state: Record<string, unknown>,
) {
  const mappingRuleKey = 'mappingRuleId' + generateUniqueId();
  await createMappingRule(request, state, mappingRuleKey);
  return {
    mappingRuleKey: mappingRuleKey,
    mappingRuleId: mappingRuleIdFromKey(mappingRuleKey, state),
    responseBody: MAPPING_RULE_EXPECTED_BODY_USING_KEY(mappingRuleKey, state),
  };
}

export function getExpectedContent(resourceName: string): string {
  const resourcePath = `./resources/${resourceName}`;
  return readFileSync(resourcePath, 'utf-8');
}

export function DECISION_DEFINITION_RESPONSE_FROM_DEPLOYMENT(
  decision: DecisionDeployment,
) {
  return {
    decisionDefinitionKey: decision.decisionDefinitionKey,
    decisionRequirementsKey: decision.decisionRequirementsKey,
    decisionRequirementsId: decision.decisionRequirementsId,
    decisionDefinitionId: decision.decisionDefinitionId,
    name: decision.name,
    version: decision.version,
    tenantId: decision.tenantId,
  };
}

// Cluster Variable beans
export const clusterVariableRequiredFields: string[] = [
  'name',
  'value',
  'scope',
];
export const clusterVariableSearchItemRequiredFields: string[] = [
  'name',
  'value',
  'scope',
  'isTruncated',
];

export function CREATE_CLUSTER_VARIABLE() {
  const uid = generateUniqueId();
  return {
    name: `cluster-var-${uid}`,
    value: {testKey: `testValue-${uid}`},
  };
}
