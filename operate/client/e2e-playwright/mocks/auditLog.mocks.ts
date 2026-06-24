/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Route} from '@playwright/test';
import {type QueryAuditLogsResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';

function mockResponses({auditLogs}: {auditLogs?: QueryAuditLogsResponseBody}) {
  return (route: Route) => {
    if (route.request().url().includes('/v2/authentication/me')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          displayName: 'demo',
          canLogout: true,
          roles: null,
          salesPlanType: null,
          c8Links: {},
          username: 'demo',
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/v2/audit-logs/search')) {
      return route.fulfill({
        status: auditLogs === undefined ? 400 : 200,
        body: JSON.stringify(auditLogs),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    return route.continue();
  };
}

const mockAuditLogs: QueryAuditLogsResponseBody = {
  items: [
    {
      auditLogKey: 'audit-log-1',
      entityKey: '6755399441062827',
      entityType: 'PROCESS_INSTANCE',
      operationType: 'CREATE',
      batchOperationKey: null,
      batchOperationType: null,
      timestamp: '2023-08-25T15:41:45.322+0000',
      actorId: 'demo',
      actorType: 'USER',
      tenantId: null,
      result: 'SUCCESS',
      category: 'DEPLOYED_RESOURCES',
      processDefinitionId: 'orderProcess',
      processDefinitionKey: '2251799813685249',
      processInstanceKey: '6755399441062827',
      rootProcessInstanceKey: null,
      elementInstanceKey: null,
      jobKey: null,
      userTaskKey: null,
      decisionRequirementsId: null,
      decisionRequirementsKey: null,
      decisionDefinitionId: null,
      decisionDefinitionKey: null,
      decisionEvaluationKey: null,
      deploymentKey: null,
      formKey: null,
      resourceKey: null,
      relatedEntityKey: null,
      relatedEntityType: null,
      entityDescription: null,
      agentElementId: null,
    },
    {
      auditLogKey: 'audit-log-2',
      entityKey: '6755399441062827',
      entityType: 'PROCESS_INSTANCE',
      operationType: 'CANCEL',
      batchOperationKey: '653ed5e6-49ed-4675-85bf-2c54a94d8180',
      batchOperationType: 'CANCEL_PROCESS_INSTANCE',
      timestamp: '2023-08-25T15:41:49.754+0000',
      actorId: 'demo',
      actorType: 'USER',
      tenantId: null,
      result: 'SUCCESS',
      category: 'DEPLOYED_RESOURCES',
      processDefinitionId: 'orderProcess',
      processDefinitionKey: '2251799813685249',
      processInstanceKey: '6755399441062827',
      rootProcessInstanceKey: null,
      elementInstanceKey: null,
      jobKey: null,
      userTaskKey: null,
      decisionRequirementsId: null,
      decisionRequirementsKey: null,
      decisionDefinitionId: null,
      decisionDefinitionKey: null,
      decisionEvaluationKey: null,
      deploymentKey: null,
      formKey: null,
      resourceKey: null,
      relatedEntityKey: null,
      relatedEntityType: null,
      entityDescription: null,
      agentElementId: null,
    },
    {
      auditLogKey: 'audit-log-3',
      entityKey: '6755399441062826',
      entityType: 'INCIDENT',
      operationType: 'RESOLVE',
      batchOperationKey: null,
      batchOperationType: null,
      timestamp: '2023-08-24T12:30:00.000+0000',
      actorId: 'admin',
      actorType: 'USER',
      tenantId: null,
      result: 'FAIL',
      category: 'DEPLOYED_RESOURCES',
      processDefinitionId: 'orderProcess',
      processDefinitionKey: '2251799813685249',
      processInstanceKey: '6755399441062826',
      rootProcessInstanceKey: null,
      elementInstanceKey: '6755399441062900',
      jobKey: null,
      userTaskKey: null,
      decisionRequirementsId: null,
      decisionRequirementsKey: null,
      decisionDefinitionId: null,
      decisionDefinitionKey: null,
      decisionEvaluationKey: null,
      deploymentKey: null,
      formKey: null,
      resourceKey: null,
      relatedEntityKey: null,
      relatedEntityType: null,
      entityDescription: null,
      agentElementId: null,
    },
  ],
  page: {
    totalItems: 3,
    startCursor: null,
    endCursor: null,
    hasMoreTotalItems: false,
  },
};

export {mockResponses, mockAuditLogs};
