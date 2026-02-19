/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Route} from '@playwright/test';
import {
  type BatchOperation,
  type GetProcessDefinitionStatisticsResponseBody,
  type QueryBatchOperationsResponseBody,
  type QueryBatchOperationItemsRequestBody,
  type QueryBatchOperationItemsResponseBody,
  type QueryProcessDefinitionsRequestBody,
  type QueryProcessDefinitionsResponseBody,
  type QueryProcessInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import type {BatchOperationDto, OperationEntity} from '@/types';

function mockResponses({
  batchOperations,
  batchOperation,
  processDefinitions,
  statistics,
  processInstances,
  processXml,
  deleteProcess,
  batchOperationItems,
}: {
  batchOperations?: QueryBatchOperationsResponseBody;
  batchOperation?: OperationEntity;
  processDefinitions?: QueryProcessDefinitionsResponseBody['items'];
  statistics?: GetProcessDefinitionStatisticsResponseBody;
  processInstances?: QueryProcessInstancesResponseBody;
  processXml?: string;
  deleteProcess?: BatchOperationDto;
  batchOperationItems?: QueryBatchOperationItemsResponseBody;
}) {
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

    if (route.request().url().includes('/v2/batch-operations/search')) {
      return route.fulfill({
        status: batchOperations === undefined ? 400 : 200,
        body: JSON.stringify(batchOperations),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/v2/batch-operation-items/search')) {
      if (!batchOperationItems) {
        return route.fulfill({
          status: 400,
          headers: {'content-type': 'application/json'},
        });
      }

      const requestBody = route
        .request()
        .postDataJSON() as QueryBatchOperationItemsRequestBody;

      let filteredItems = batchOperationItems.items;

      const batchOperationKeyFilter = requestBody.filter?.batchOperationKey;
      if (
        batchOperationKeyFilter &&
        typeof batchOperationKeyFilter === 'object' &&
        '$eq' in batchOperationKeyFilter &&
        batchOperationKeyFilter.$eq
      ) {
        filteredItems = filteredItems.filter(
          (item) => item.batchOperationKey === batchOperationKeyFilter.$eq,
        );
      }

      const stateFilter = requestBody.filter?.state;
      if (
        stateFilter &&
        typeof stateFilter === 'object' &&
        '$eq' in stateFilter &&
        stateFilter.$eq
      ) {
        filteredItems = filteredItems.filter(
          (item) => item.state === stateFilter.$eq,
        );
      }

      const processInstanceKeyFilter = requestBody.filter?.processInstanceKey;
      if (
        processInstanceKeyFilter &&
        typeof processInstanceKeyFilter === 'object' &&
        '$in' in processInstanceKeyFilter &&
        processInstanceKeyFilter.$in
      ) {
        filteredItems = filteredItems.filter((item) =>
          processInstanceKeyFilter.$in!.includes(item.processInstanceKey),
        );
      }

      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          items: filteredItems,
          page: {totalItems: filteredItems.length},
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (
      route.request().url().includes('/api/process-instances/batch-operation')
    ) {
      return route.fulfill({
        status: batchOperation === undefined ? 400 : 200,
        body: JSON.stringify(batchOperation),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/v2/process-definitions/search')) {
      if (!processDefinitions) {
        return route.fulfill({
          status: 400,
          headers: {'content-type': 'application/json'},
        });
      }
      const query = route
        .request()
        .postDataJSON() as QueryProcessDefinitionsRequestBody;

      let matchingDefinitions = processDefinitions;
      if (query.filter?.processDefinitionId) {
        matchingDefinitions = processDefinitions.filter(
          (d) => d.processDefinitionId === query.filter?.processDefinitionId,
        );
      }
      if (query.filter?.version !== undefined) {
        matchingDefinitions = matchingDefinitions.filter(
          (d) => d.version === query.filter?.version,
        );
      }

      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          items: matchingDefinitions,
          page: {totalItems: matchingDefinitions.length},
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('statistics/element-instances')) {
      return route.fulfill({
        status: statistics === undefined ? 400 : 200,
        body: JSON.stringify(statistics),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/v2/process-instances/search')) {
      return route.fulfill({
        status: processInstances === undefined ? 400 : 200,
        body: JSON.stringify(processInstances),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (
      route
        .request()
        .url()
        .match(/\/v2\/process-definitions\/\d+\/xml/)
    ) {
      return route.fulfill({
        status: processXml === undefined ? 400 : 200,
        body: processXml,
        headers: {
          'content-type': 'application/text',
        },
      });
    }

    if (route.request().url().includes('xml')) {
      return route.fulfill({
        status: processXml === undefined ? 400 : 200,
        body: JSON.stringify(processXml),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (
      route.request().url().includes('/api/processes') &&
      route.request().method() === 'DELETE'
    ) {
      return route.fulfill({
        status: deleteProcess === undefined ? 400 : 200,
        body: JSON.stringify(deleteProcess),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    route.continue();
  };
}

const mockProcessDefinitions: QueryProcessDefinitionsResponseBody['items'] = [
  {
    processDefinitionKey: '2251799813685249',
    name: 'Always completing process',
    version: 1,
    processDefinitionId: 'always-completing-process',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813685430',
    name: 'Big variable process',
    version: 1,
    processDefinitionId: 'bigVarProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686145',
    name: 'Call Activity Process',
    version: 1,
    processDefinitionId: 'call-activity-process',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686130',
    name: 'DMN invoice',
    version: 1,
    processDefinitionId: 'invoice',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686159',
    name: 'Data store process',
    version: 1,
    processDefinitionId: 'dataStoreProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686151',
    name: 'Error Process',
    version: 1,
    processDefinitionId: 'errorProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813687212',
    name: 'Escalation events',
    version: 2,
    processDefinitionId: 'escalationEvents',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686163',
    name: 'Escalation events',
    version: 1,
    processDefinitionId: 'escalationEvents',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686147',
    name: 'Event Subprocess Process',
    version: 1,
    processDefinitionId: 'eventSubprocessProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813687203',
    name: 'Event based gateway with timer start',
    version: 2,
    processDefinitionId: 'eventBasedGatewayProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686134',
    name: 'Event based gateway with message start',
    version: 1,
    processDefinitionId: 'eventBasedGatewayProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813687190',
    name: 'Flight registration',
    version: 2,
    processDefinitionId: 'flightRegistration',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686118',
    name: 'Flight registration',
    version: 1,
    processDefinitionId: 'flightRegistration',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686168',
    name: 'Inclusive gateway',
    version: 1,
    processDefinitionId: 'inclusiveGatewayProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813685251',
    name: 'Input Output Mapping Test',
    version: 1,
    processDefinitionId: 'Process_b1711b2e-ec8e-4dad-908c-8c12e028f32f',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686161',
    name: 'Link events process',
    version: 1,
    processDefinitionId: 'linkEventProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813687192',
    name: 'Multi-Instance Process',
    version: 2,
    processDefinitionId: 'multiInstanceProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686120',
    name: 'Sequential Multi-Instance Process',
    version: 1,
    processDefinitionId: 'multiInstanceProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686137',
    name: 'Nested subprocesses',
    version: 1,
    processDefinitionId: 'prWithSubprocess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813685301',
    name: 'Only Incidents Process',
    version: 2,
    processDefinitionId: 'onlyIncidentsProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813685257',
    name: 'Only Incidents Process',
    version: 1,
    processDefinitionId: 'onlyIncidentsProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686114',
    name: 'Order process',
    version: 1,
    processDefinitionId: 'orderProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686165',
    name: 'Signal event',
    version: 1,
    processDefinitionId: 'signalEventProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686155',
    name: 'Terminate End Event',
    version: 1,
    processDefinitionId: 'terminateEndEvent',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813687198',
    name: 'Timer process',
    version: 2,
    processDefinitionId: 'timerProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686143',
    name: 'Timer process',
    version: 1,
    processDefinitionId: 'timerProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813685364',
    name: 'Without Incidents Process',
    version: 2,
    processDefinitionId: 'withoutIncidentsProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813685350',
    name: 'Without Incidents Process',
    version: 1,
    processDefinitionId: 'withoutIncidentsProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813685255',
    name: 'Without Instances Process',
    version: 2,
    processDefinitionId: 'noInstancesProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813685253',
    name: 'Without Instances Process',
    version: 1,
    processDefinitionId: 'noInstancesProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686157',
    name: 'undefined-task',
    version: 1,
    processDefinitionId: 'undefined-task-process',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686149',
    name: undefined,
    version: 1,
    processDefinitionId: 'bigProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813687891',
    name: undefined,
    version: 2,
    processDefinitionId: 'called-process',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813687210',
    name: 'Called Process',
    version: 1,
    processDefinitionId: 'called-process',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813687889',
    name: undefined,
    version: 3,
    processDefinitionId: 'complexProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813687201',
    name: undefined,
    version: 2,
    processDefinitionId: 'complexProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686132',
    name: undefined,
    version: 1,
    processDefinitionId: 'complexProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686153',
    name: undefined,
    version: 1,
    processDefinitionId: 'error-end-process',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686124',
    name: undefined,
    version: 1,
    processDefinitionId: 'intermediate-message-throw-event-process',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686126',
    name: undefined,
    version: 1,
    processDefinitionId: 'intermediate-none-event-process',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813687206',
    name: undefined,
    version: 2,
    processDefinitionId: 'interruptingBoundaryEvent',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686139',
    name: undefined,
    version: 1,
    processDefinitionId: 'interruptingBoundaryEvent',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686116',
    name: undefined,
    version: 1,
    processDefinitionId: 'loanProcess',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686122',
    name: undefined,
    version: 1,
    processDefinitionId: 'manual-task-process',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686128',
    name: undefined,
    version: 1,
    processDefinitionId: 'message-end-event-process',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813687208',
    name: undefined,
    version: 2,
    processDefinitionId: 'nonInterruptingBoundaryEvent',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686141',
    name: undefined,
    version: 1,
    processDefinitionId: 'nonInterruptingBoundaryEvent',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686133',
    name: 'Lots Of Tasks',
    version: 1,
    processDefinitionId: 'LotsOfTasks',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
  {
    processDefinitionKey: '2251799813686145',
    name: 'Lots Of Tasks',
    version: 2,
    processDefinitionId: 'LotsOfTasks',
    versionTag: undefined,
    tenantId: '<default>',
    hasStartForm: false,
  },
];

const mockNewDeleteOperation: BatchOperation = {
  batchOperationKey: '653ed5e6-49ed-4675-85bf-2c54a94d8180',
  batchOperationType: 'RESOLVE_INCIDENT',
  startDate: '2023-08-25T15:41:45.322+0300',
  endDate: '2023-08-25T15:41:49.754+0300',
  operationsTotalCount: 3,
  operationsFailedCount: 0,
  operationsCompletedCount: 3,
  state: 'COMPLETED',
};

const mockBatchOperations: QueryBatchOperationsResponseBody = {
  items: [
    {
      batchOperationKey: '653ed5e6-49ed-4675-85bf-2c54a94d8180',
      batchOperationType: 'RESOLVE_INCIDENT',
      startDate: '2023-08-25T15:41:45.322+0300',
      endDate: '2023-08-25T15:41:49.754+0300',
      operationsTotalCount: 3,
      operationsFailedCount: 0,
      operationsCompletedCount: 3,
      state: 'COMPLETED',
    },
    {
      batchOperationKey: '35ccdcfc-aeac-4ec8-ac6c-db67e581b22e',
      batchOperationType: 'MODIFY_PROCESS_INSTANCE',
      startDate: '2023-08-15T10:42:17.548+0300',
      endDate: '2023-08-15T10:42:18.818+0300',
      operationsTotalCount: 5,
      operationsFailedCount: 2,
      operationsCompletedCount: 3,
      state: 'COMPLETED',
    },
    {
      batchOperationKey: 'fb7cfeb0-abaa-4323-8910-9d44fe031c08',
      batchOperationType: 'CANCEL_PROCESS_INSTANCE',
      startDate: '2023-08-14T08:46:05.677+0300',
      endDate: '2023-08-14T08:46:25.020+0300',
      operationsTotalCount: 3,
      operationsFailedCount: 3,
      operationsCompletedCount: 0,
      state: 'COMPLETED',
    },
    {
      batchOperationKey: 'c1331a55-3f6f-4884-837f-dfa268f7ef0c',
      batchOperationType: 'CANCEL_PROCESS_INSTANCE',
      startDate: '2023-08-14T08:46:05.459+0300',
      endDate: '2023-08-14T08:46:25.010+0300',
      operationsTotalCount: 1,
      operationsFailedCount: 1,
      operationsCompletedCount: 0,
      state: 'COMPLETED',
    },
    {
      batchOperationKey: 'a74db3d1-4588-41a5-9e10-42cea80213a6',
      batchOperationType: 'CANCEL_PROCESS_INSTANCE',
      startDate: '2023-08-14T08:46:06.164+0300',
      endDate: '2023-08-14T08:46:14.965+0300',
      operationsTotalCount: 1,
      operationsFailedCount: 0,
      operationsCompletedCount: 1,
      state: 'COMPLETED',
    },
    {
      batchOperationKey: '9961d35a-261f-4b29-b506-8b14cc6e7992',
      batchOperationType: 'CANCEL_PROCESS_INSTANCE',
      startDate: '2023-08-14T08:46:05.569+0300',
      endDate: '2023-08-14T08:46:14.942+0300',
      operationsTotalCount: 1,
      operationsFailedCount: 0,
      operationsCompletedCount: 1,
      state: 'COMPLETED',
    },
    {
      batchOperationKey: 'b1454600-5f13-4365-bb45-960e8372136b',
      batchOperationType: 'RESOLVE_INCIDENT',
      startDate: '2023-08-18T13:14:32.297+0300',
      endDate: '2023-08-18T13:14:37.023+0300',
      operationsTotalCount: 1,
      operationsFailedCount: 1,
      operationsCompletedCount: 0,
      state: 'PARTIALLY_COMPLETED',
    },
    {
      batchOperationKey: 'f9ddd801-ff34-44da-8d7c-366036b6d8d8',
      batchOperationType: 'CANCEL_PROCESS_INSTANCE',
      startDate: '2023-08-14T08:46:06.344+0300',
      endDate: '2023-08-14T08:46:14.987+0300',
      operationsTotalCount: 1,
      operationsFailedCount: 0,
      operationsCompletedCount: 1,
      state: 'COMPLETED',
    },
    {
      batchOperationKey: 'c5e97ca8-bdf9-434f-934f-506a6960d1e3',
      batchOperationType: 'RESOLVE_INCIDENT',
      startDate: '2023-08-15T13:17:32.235+0300',
      endDate: '2023-08-15T13:17:36.637+0300',
      operationsTotalCount: 1,
      operationsFailedCount: 0,
      operationsCompletedCount: 1,
      state: 'COMPLETED',
    },
    // Active (no endDate yet)
    {
      batchOperationKey: '653ed5e6-49ed-4675-85bf-2c54a94d8199',
      batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
      startDate: '2023-10-01T10:00:00.000+0000',
      endDate: undefined,
      operationsTotalCount: 3,
      operationsFailedCount: 0,
      operationsCompletedCount: 0,
      state: 'ACTIVE',
    },
  ],
  page: {
    totalItems: 10,
  },
};

const mockProcessInstances: QueryProcessInstancesResponseBody = {
  items: [
    {
      processInstanceKey: '2251799813934753',
      processDefinitionKey: '2251799813687198',
      processDefinitionName: 'Timer process',
      processDefinitionVersion: 2,
      startDate: '2023-08-28T12:52:47.586+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: false,
      processDefinitionId: 'timerProcess',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813905557',
      processDefinitionKey: '2251799813687203',
      processDefinitionName: 'Event based gateway with timer start',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:48:37.633+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'eventBasedGatewayProcess',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813905508',
      processDefinitionKey: '2251799813687203',
      processDefinitionName: 'Event based gateway with timer start',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:48:27.644+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'eventBasedGatewayProcess',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813902229',
      processDefinitionKey: '2251799813687203',
      processDefinitionName: 'Event based gateway with timer start',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:48:17.718+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'eventBasedGatewayProcess',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813896694',
      processDefinitionKey: '2251799813687203',
      processDefinitionName: 'Event based gateway with timer start',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:48:07.653+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'eventBasedGatewayProcess',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813893246',
      processDefinitionKey: '2251799813687203',
      processDefinitionName: 'Event based gateway with timer start',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:57.664+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'eventBasedGatewayProcess',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813889623',
      processDefinitionKey: '2251799813687203',
      processDefinitionName: 'Event based gateway with timer start',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:47.660+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'eventBasedGatewayProcess',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813885633',
      processDefinitionKey: '2251799813687203',
      processDefinitionName: 'Event based gateway with timer start',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:37.629+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'eventBasedGatewayProcess',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813880821',
      processDefinitionKey: '2251799813687203',
      processDefinitionName: 'Event based gateway with timer start',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:27.628+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'eventBasedGatewayProcess',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813855554',
      processDefinitionKey: '2251799813687203',
      processDefinitionName: 'Event based gateway with timer start',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:17.605+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'eventBasedGatewayProcess',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813831475',
      processDefinitionKey: '2251799813687203',
      processDefinitionName: 'Event based gateway with timer start',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.622+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'eventBasedGatewayProcess',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '6755399441062811',
      processDefinitionKey: '2251799813686145',
      processDefinitionName: 'Call Activity Process',
      processDefinitionVersion: 1,
      startDate: '2023-08-14T05:47:07.376+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'call-activity-process',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '6755399441062817',
      processDefinitionKey: '2251799813687891',
      processDefinitionName: 'called-process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.376+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'called-process',
      parentProcessInstanceKey: '6755399441062811',
      tenantId: '',
    },
    {
      processInstanceKey: '6755399441062827',
      processDefinitionKey: '2251799813687188',
      processDefinitionName: 'Order process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.376+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'orderProcess',
      parentProcessInstanceKey: '6755399441062817',
      tenantId: '',
    },
    {
      processInstanceKey: '6755399441062833',
      processDefinitionKey: '2251799813687188',
      processDefinitionName: 'Order process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.376+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'orderProcess',
      parentProcessInstanceKey: '6755399441062817',
      tenantId: '',
    },
    {
      processInstanceKey: '4503599627376291',
      processDefinitionKey: '2251799813687889',
      processDefinitionName: 'complexProcess',
      processDefinitionVersion: 3,
      startDate: '2023-08-14T05:47:07.373+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'complexProcess',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813831341',
      processDefinitionKey: '2251799813686145',
      processDefinitionName: 'Call Activity Process',
      processDefinitionVersion: 1,
      startDate: '2023-08-14T05:47:07.361+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'call-activity-process',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813831347',
      processDefinitionKey: '2251799813687891',
      processDefinitionName: 'called-process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.361+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'called-process',
      parentProcessInstanceKey: '2251799813831341',
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813831357',
      processDefinitionKey: '2251799813687188',
      processDefinitionName: 'Order process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.361+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'orderProcess',
      parentProcessInstanceKey: '2251799813831347',
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813831363',
      processDefinitionKey: '2251799813687188',
      processDefinitionName: 'Order process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.361+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'orderProcess',
      parentProcessInstanceKey: '2251799813831347',
      tenantId: '',
    },
    {
      processInstanceKey: '9007199254749246',
      processDefinitionKey: '2251799813687889',
      processDefinitionName: 'complexProcess',
      processDefinitionVersion: 3,
      startDate: '2023-08-14T05:47:07.359+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'complexProcess',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '6755399441062775',
      processDefinitionKey: '2251799813686145',
      processDefinitionName: 'Call Activity Process',
      processDefinitionVersion: 1,
      startDate: '2023-08-14T05:47:07.355+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'call-activity-process',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '6755399441062781',
      processDefinitionKey: '2251799813687891',
      processDefinitionName: 'called-process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.355+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'called-process',
      parentProcessInstanceKey: '6755399441062775',
      tenantId: '',
    },
    {
      processInstanceKey: '6755399441062791',
      processDefinitionKey: '2251799813687188',
      processDefinitionName: 'Order process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.355+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'orderProcess',
      parentProcessInstanceKey: '6755399441062781',
      tenantId: '',
    },
    {
      processInstanceKey: '6755399441062797',
      processDefinitionKey: '2251799813687188',
      processDefinitionName: 'Order process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.355+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'orderProcess',
      parentProcessInstanceKey: '6755399441062781',
      tenantId: '',
    },
    {
      processInstanceKey: '4503599627376266',
      processDefinitionKey: '2251799813687889',
      processDefinitionName: 'complexProcess',
      processDefinitionVersion: 3,
      startDate: '2023-08-14T05:47:07.352+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'complexProcess',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813831303',
      processDefinitionKey: '2251799813686145',
      processDefinitionName: 'Call Activity Process',
      processDefinitionVersion: 1,
      startDate: '2023-08-14T05:47:07.348+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'call-activity-process',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813831309',
      processDefinitionKey: '2251799813687891',
      processDefinitionName: 'called-process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.348+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'called-process',
      parentProcessInstanceKey: '2251799813831303',
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813831319',
      processDefinitionKey: '2251799813687188',
      processDefinitionName: 'Order process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.348+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'orderProcess',
      parentProcessInstanceKey: '2251799813831309',
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813831325',
      processDefinitionKey: '2251799813687188',
      processDefinitionName: 'Order process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.348+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'orderProcess',
      parentProcessInstanceKey: '2251799813831309',
      tenantId: '',
    },
    {
      processInstanceKey: '9007199254749223',
      processDefinitionKey: '2251799813687889',
      processDefinitionName: 'complexProcess',
      processDefinitionVersion: 3,
      startDate: '2023-08-14T05:47:07.345+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'complexProcess',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '6755399441062739',
      processDefinitionKey: '2251799813686145',
      processDefinitionName: 'Call Activity Process',
      processDefinitionVersion: 1,
      startDate: '2023-08-14T05:47:07.339+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'call-activity-process',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '6755399441062745',
      processDefinitionKey: '2251799813687891',
      processDefinitionName: 'called-process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.339+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'called-process',
      parentProcessInstanceKey: '6755399441062739',
      tenantId: '',
    },
    {
      processInstanceKey: '6755399441062755',
      processDefinitionKey: '2251799813687188',
      processDefinitionName: 'Order process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.339+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'orderProcess',
      parentProcessInstanceKey: '6755399441062745',
      tenantId: '',
    },
    {
      processInstanceKey: '6755399441062761',
      processDefinitionKey: '2251799813687188',
      processDefinitionName: 'Order process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.339+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'orderProcess',
      parentProcessInstanceKey: '6755399441062745',
      tenantId: '',
    },
    {
      processInstanceKey: '4503599627376242',
      processDefinitionKey: '2251799813687889',
      processDefinitionName: 'complexProcess',
      processDefinitionVersion: 3,
      startDate: '2023-08-14T05:47:07.337+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'complexProcess',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813831264',
      processDefinitionKey: '2251799813686145',
      processDefinitionName: 'Call Activity Process',
      processDefinitionVersion: 1,
      startDate: '2023-08-14T05:47:07.334+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'call-activity-process',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813831270',
      processDefinitionKey: '2251799813687891',
      processDefinitionName: 'called-process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.334+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'called-process',
      parentProcessInstanceKey: '2251799813831264',
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813831280',
      processDefinitionKey: '2251799813687188',
      processDefinitionName: 'Order process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.334+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'orderProcess',
      parentProcessInstanceKey: '2251799813831270',
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813831286',
      processDefinitionKey: '2251799813687188',
      processDefinitionName: 'Order process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.334+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'orderProcess',
      parentProcessInstanceKey: '2251799813831270',
      tenantId: '',
    },
    {
      processInstanceKey: '9007199254749200',
      processDefinitionKey: '2251799813687889',
      processDefinitionName: 'complexProcess',
      processDefinitionVersion: 3,
      startDate: '2023-08-14T05:47:07.328+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'complexProcess',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '6755399441062705',
      processDefinitionKey: '2251799813686145',
      processDefinitionName: 'Call Activity Process',
      processDefinitionVersion: 1,
      startDate: '2023-08-14T05:47:07.325+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'call-activity-process',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '6755399441062711',
      processDefinitionKey: '2251799813687891',
      processDefinitionName: 'called-process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.325+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'called-process',
      parentProcessInstanceKey: '6755399441062705',
      tenantId: '',
    },
    {
      processInstanceKey: '6755399441062721',
      processDefinitionKey: '2251799813687188',
      processDefinitionName: 'Order process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.325+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'orderProcess',
      parentProcessInstanceKey: '6755399441062711',
      tenantId: '',
    },
    {
      processInstanceKey: '6755399441062727',
      processDefinitionKey: '2251799813687188',
      processDefinitionName: 'Order process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.325+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'orderProcess',
      parentProcessInstanceKey: '6755399441062711',
      tenantId: '',
    },
    {
      processInstanceKey: '4503599627376219',
      processDefinitionKey: '2251799813687889',
      processDefinitionName: 'complexProcess',
      processDefinitionVersion: 3,
      startDate: '2023-08-14T05:47:07.320+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'complexProcess',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813831225',
      processDefinitionKey: '2251799813686145',
      processDefinitionName: 'Call Activity Process',
      processDefinitionVersion: 1,
      startDate: '2023-08-14T05:47:07.317+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'call-activity-process',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813831231',
      processDefinitionKey: '2251799813687891',
      processDefinitionName: 'called-process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.317+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'called-process',
      parentProcessInstanceKey: '2251799813831225',
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813831241',
      processDefinitionKey: '2251799813687188',
      processDefinitionName: 'Order process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.317+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'orderProcess',
      parentProcessInstanceKey: '2251799813831231',
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813831247',
      processDefinitionKey: '2251799813687188',
      processDefinitionName: 'Order process',
      processDefinitionVersion: 2,
      startDate: '2023-08-14T05:47:07.317+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: true,
      processDefinitionId: 'orderProcess',
      parentProcessInstanceKey: '2251799813831231',
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813838240',
      processDefinitionKey: '2251799813686133',
      processDefinitionName: 'Lots Of Tasks',
      processDefinitionVersion: 1,
      startDate: '2023-08-28T12:52:47.586+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: false,
      processDefinitionId: 'LotsOfTasks',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
    {
      processInstanceKey: '2251799813838240',
      processDefinitionKey: '2251799813686145',
      processDefinitionName: 'Lots Of Tasks',
      processDefinitionVersion: 2,
      startDate: '2023-08-28T12:52:47.586+0000',
      endDate: undefined,
      state: 'ACTIVE',
      hasIncident: false,
      processDefinitionId: 'LotsOfTasks',
      parentProcessInstanceKey: undefined,
      tenantId: '',
    },
  ],
  page: {
    totalItems: 891,
  },
};

const mockProcessInstancesWithOperationError: QueryProcessInstancesResponseBody =
  {
    items: [
      {
        processInstanceKey: '6755399441062827',
        processDefinitionKey: '2251799813687188',
        processDefinitionName: 'Order process',
        processDefinitionVersion: 2,
        startDate: '2023-08-14T05:47:07.376+0000',
        endDate: undefined,
        state: 'ACTIVE',
        hasIncident: true,
        processDefinitionId: 'orderProcess',
        parentProcessInstanceKey: '6755399441062817',
        tenantId: '',
      },
      {
        processInstanceKey: '6755399441062826',
        processDefinitionKey: '2251799813687187',
        processDefinitionName: 'Order process',
        processDefinitionVersion: 2,
        startDate: '2023-08-14T05:47:07.376+0000',
        endDate: undefined,
        state: 'ACTIVE',
        hasIncident: false,
        processDefinitionId: 'orderProcess',
        parentProcessInstanceKey: '6755399441062816',
        tenantId: '',
      },
    ],
    page: {
      totalItems: 2,
    },
  };

const mockOrderProcessDefinitions: QueryProcessDefinitionsResponseBody['items'] =
  [
    {
      processDefinitionKey: '2251799813686114',
      name: 'Order process',
      version: 2,
      processDefinitionId: 'orderProcess',
      versionTag: undefined,
      tenantId: '<default>',
      hasStartForm: false,
    },
    {
      processDefinitionKey: '2251799813686114',
      name: 'Order process',
      version: 1,
      processDefinitionId: 'orderProcess',
      versionTag: undefined,
      tenantId: '<default>',
      hasStartForm: false,
    },
  ];

const mockOrderProcessInstances: QueryProcessInstancesResponseBody = {
  page: {
    totalItems: 20,
  },
  items: Array(20)
    .fill(0)
    .map((_, index) => {
      return {
        processInstanceKey: `22517998139543${index.toString().padStart(2, '0')}`,
        processDefinitionKey: '2251799813687188',
        processDefinitionName: 'Order process',
        processDefinitionVersion: 1,
        startDate: '2023-09-29T14:12:11.684+0000',
        endDate: undefined,
        state: 'ACTIVE',
        processDefinitionId: 'orderProcess',
        parentProcessInstanceKey: undefined,
        tenantId: '<default>',
        hasIncident: false,
      };
    }),
};

const mockFinishedOrderProcessInstances: QueryProcessInstancesResponseBody = {
  page: {
    totalItems: mockOrderProcessInstances.page.totalItems,
  },
  items: mockOrderProcessInstances.items.map((instance) => {
    return {
      ...instance,
      state: 'COMPLETED',
      endDate: '2023-09-29T15:16:11.684+0000',
    };
  }),
};

const mockOrderProcessInstancesWithFailedOperations: QueryProcessInstancesResponseBody =
  {
    page: {
      totalItems: mockOrderProcessInstances.page.totalItems,
    },
    items: mockOrderProcessInstances.items,
  };

const mockOrderProcessV2Instances: QueryProcessInstancesResponseBody = {
  page: {
    totalItems: 3,
  },
  items: Array(3)
    .fill(0)
    .map((_, index) => {
      return {
        processInstanceKey: `22517998139543${index}`,
        processDefinitionKey: '2251799813687188',
        processDefinitionName: '',
        processDefinitionVersion: 2,
        startDate: '2023-09-29T14:12:11.684+0000',
        endDate: undefined,
        state: 'ACTIVE',
        processDefinitionId: 'orderProcess',
        parentProcessInstanceKey: undefined,
        tenantId: '<default>',
        hasIncident: false,
      };
    }),
};

const mockAhspProcessDefinitions: QueryProcessDefinitionsResponseBody['items'] =
  [
    {
      processDefinitionId: 'migration-ahsp-process_v1',
      version: 1,
      name: 'Ad Hoc Subprocess Source',
      processDefinitionKey: '2251799813685249',
      tenantId: '<default>',
      hasStartForm: false,
    },
    {
      processDefinitionId: 'migration-ahsp-process_v2',
      version: 2,
      name: 'Ad Hoc Subprocess Target',
      processDefinitionKey: '2251799813685250',
      tenantId: '<default>',
      hasStartForm: false,
    },
  ];

const mockAhspProcessInstances: QueryProcessInstancesResponseBody = {
  page: {
    totalItems: 3,
  },
  items: [
    {
      processInstanceKey: '2251799813685251',
      processDefinitionKey: '2251799813685249',
      processDefinitionName: 'Ad Hoc Subprocess Source',
      processDefinitionVersion: 1,
      startDate: '2023-10-10T08:30:00.000+0000',
      endDate: undefined,
      state: 'ACTIVE',
      processDefinitionId: 'migration-ahsp-process_v1',
      parentProcessInstanceKey: undefined,
      tenantId: '<default>',
      hasIncident: false,
    },
    {
      processInstanceKey: '2251799813685252',
      processDefinitionKey: '2251799813685249',
      processDefinitionName: 'Ad Hoc Subprocess Source',
      processDefinitionVersion: 1,
      startDate: '2023-10-10T08:31:00.000+0000',
      endDate: undefined,
      state: 'ACTIVE',
      processDefinitionId: 'migration-ahsp-process_v1',
      parentProcessInstanceKey: undefined,
      tenantId: '<default>',
      hasIncident: false,
    },
    {
      processInstanceKey: '2251799813685253',
      processDefinitionKey: '2251799813685249',
      processDefinitionName: 'Ad Hoc Subprocess Source',
      processDefinitionVersion: 1,
      startDate: '2023-10-10T08:32:00.000+0000',
      endDate: undefined,
      state: 'ACTIVE',
      processDefinitionId: 'migration-ahsp-process_v1',
      parentProcessInstanceKey: undefined,
      tenantId: '<default>',
      hasIncident: false,
    },
  ],
};

const mockStatistics = {
  items: [
    {
      elementId: 'eventSubprocess',
      active: 9,
      canceled: 2,
      incidents: 0,
      completed: 0,
    },
    {
      elementId: 'EndEvent_1uddjvh',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 9,
    },
    {
      elementId: 'ServiceTask_1daop2o',
      active: 0,
      canceled: 20,
      incidents: 0,
      completed: 0,
    },
    {
      elementId: 'eventSubprocessTask',
      active: 0,
      canceled: 2,
      incidents: 9,
      completed: 0,
    },
  ],
};

const mockProcessXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" id="Definitions_0uef7zo" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.3.0">
  <bpmn:process id="eventSubprocessProcess" name="Event Subprocess Process" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1vnazga">
      <bpmn:outgoing>SequenceFlow_0b1strv</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:endEvent id="EndEvent_03acvim">
      <bpmn:incoming>SequenceFlow_0ogmd2w</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="SequenceFlow_0b1strv" sourceRef="StartEvent_1vnazga" targetRef="ServiceTask_1daop2o" />
    <bpmn:subProcess id="eventSubprocess" name="Event Subprocess" triggeredByEvent="true">
      <bpmn:endEvent id="EndEvent_1uddjvh">
        <bpmn:incoming>SequenceFlow_10d38p0</bpmn:incoming>
      </bpmn:endEvent>
      <bpmn:serviceTask id="eventSubprocessTask" name="Event Subprocess task">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="eventSupbprocessTask" />
        </bpmn:extensionElements>
        <bpmn:incoming>SequenceFlow_0xk369x</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_10d38p0</bpmn:outgoing>
      </bpmn:serviceTask>
      <bpmn:sequenceFlow id="SequenceFlow_10d38p0" sourceRef="eventSubprocessTask" targetRef="EndEvent_1uddjvh" />
      <bpmn:sequenceFlow id="SequenceFlow_0xk369x" sourceRef="StartEvent_1u9mwoj" targetRef="eventSubprocessTask" />
      <bpmn:startEvent id="StartEvent_1u9mwoj" name="Interrupting timer">
        <bpmn:outgoing>SequenceFlow_0xk369x</bpmn:outgoing>
        <bpmn:timerEventDefinition>
          <bpmn:timeDuration xsi:type="bpmn:tFormalExpression">PT15S</bpmn:timeDuration>
        </bpmn:timerEventDefinition>
      </bpmn:startEvent>
    </bpmn:subProcess>
    <bpmn:serviceTask id="ServiceTask_1daop2o" name="Parent process task">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="parentProcessTask" />
      </bpmn:extensionElements>
      <bpmn:incoming>SequenceFlow_0b1strv</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_1aytoqp</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:sequenceFlow id="SequenceFlow_1aytoqp" sourceRef="ServiceTask_1daop2o" targetRef="subprocess" />
    <bpmn:subProcess id="subprocess">
      <bpmn:incoming>SequenceFlow_1aytoqp</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_0ogmd2w</bpmn:outgoing>
      <bpmn:startEvent id="StartEvent_1dgs6mf">
        <bpmn:outgoing>SequenceFlow_03jyud1</bpmn:outgoing>
      </bpmn:startEvent>
      <bpmn:serviceTask id="ServiceTask_0wfdfpx" name="Subprocess task">
        <bpmn:extensionElements>
          <zeebe:taskDefinition type="subprocessTask" />
        </bpmn:extensionElements>
        <bpmn:incoming>SequenceFlow_03jyud1</bpmn:incoming>
        <bpmn:outgoing>SequenceFlow_1ey1yvq</bpmn:outgoing>
      </bpmn:serviceTask>
      <bpmn:sequenceFlow id="SequenceFlow_03jyud1" sourceRef="StartEvent_1dgs6mf" targetRef="ServiceTask_0wfdfpx" />
      <bpmn:endEvent id="EndEvent_171a64z">
        <bpmn:incoming>SequenceFlow_1ey1yvq</bpmn:incoming>
      </bpmn:endEvent>
      <bpmn:sequenceFlow id="SequenceFlow_1ey1yvq" sourceRef="ServiceTask_0wfdfpx" targetRef="EndEvent_171a64z" />
      <bpmn:subProcess id="SubProcess_006dg16" name="Event Subprocess inside Subprocess" triggeredByEvent="true">
        <bpmn:endEvent id="EndEvent_0dq3i8l">
          <bpmn:incoming>SequenceFlow_0vkqogh</bpmn:incoming>
        </bpmn:endEvent>
        <bpmn:serviceTask id="ServiceTask_0cj9pdg" name="Task in sub-subprocess">
          <bpmn:extensionElements>
            <zeebe:taskDefinition type="subSubprocessTask" />
          </bpmn:extensionElements>
          <bpmn:incoming>SequenceFlow_1c82aad</bpmn:incoming>
          <bpmn:outgoing>SequenceFlow_0vkqogh</bpmn:outgoing>
        </bpmn:serviceTask>
        <bpmn:sequenceFlow id="SequenceFlow_1c82aad" sourceRef="StartEvent_0kpitfv" targetRef="ServiceTask_0cj9pdg" />
        <bpmn:sequenceFlow id="SequenceFlow_0vkqogh" sourceRef="ServiceTask_0cj9pdg" targetRef="EndEvent_0dq3i8l" />
        <bpmn:startEvent id="StartEvent_0kpitfv" name="Timer in sub-subprocess" isInterrupting="false">
          <bpmn:outgoing>SequenceFlow_1c82aad</bpmn:outgoing>
          <bpmn:timerEventDefinition>
            <bpmn:timeCycle xsi:type="bpmn:tFormalExpression">R2/PT5S</bpmn:timeCycle>
          </bpmn:timerEventDefinition>
        </bpmn:startEvent>
      </bpmn:subProcess>
    </bpmn:subProcess>
    <bpmn:sequenceFlow id="SequenceFlow_0ogmd2w" sourceRef="subprocess" targetRef="EndEvent_03acvim" />
  </bpmn:process>
  <bpmn:message id="Message_03ggk3d" name="interruptProcess">
    <bpmn:extensionElements>
      <zeebe:subscription correlationKey="=clientId" />
    </bpmn:extensionElements>
  </bpmn:message>
  <bpmn:message id="Message_1nvz8ri" name="continueProcess">
    <bpmn:extensionElements>
      <zeebe:subscription correlationKey="=clientId" />
    </bpmn:extensionElements>
  </bpmn:message>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="eventSubprocessProcess">
      <bpmndi:BPMNShape id="StartEvent_1vnazga_di" bpmnElement="StartEvent_1vnazga">
        <dc:Bounds x="212" y="252" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_03acvim_di" bpmnElement="EndEvent_03acvim">
        <dc:Bounds x="1202" y="242" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="SubProcess_1u7mexg_di" bpmnElement="eventSubprocess" isExpanded="true">
        <dc:Bounds x="200" y="500" width="388" height="180" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1uddjvh_di" bpmnElement="EndEvent_1uddjvh">
        <dc:Bounds x="512" y="582" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_0h8cwwl_di" bpmnElement="eventSubprocessTask">
        <dc:Bounds x="350" y="560" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="StartEvent_0d2wour_di" bpmnElement="StartEvent_1u9mwoj">
        <dc:Bounds x="252" y="582" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="228" y="625" width="85" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_10d38p0_di" bpmnElement="SequenceFlow_10d38p0">
        <di:waypoint x="450" y="600" />
        <di:waypoint x="512" y="600" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_0xk369x_di" bpmnElement="SequenceFlow_0xk369x">
        <di:waypoint x="288" y="600" />
        <di:waypoint x="350" y="600" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="ServiceTask_1daop2o_di" bpmnElement="ServiceTask_1daop2o">
        <dc:Bounds x="344" y="230" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="SubProcess_1aoke6f_di" bpmnElement="subprocess" isExpanded="true">
        <dc:Bounds x="530" y="85" width="590" height="370" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="StartEvent_1dgs6mf_di" bpmnElement="StartEvent_1dgs6mf">
        <dc:Bounds x="660.3333333333333" y="167" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_0wfdfpx_di" bpmnElement="ServiceTask_0wfdfpx">
        <dc:Bounds x="740" y="145" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_171a64z_di" bpmnElement="EndEvent_171a64z">
        <dc:Bounds x="882" y="167" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="SubProcess_006dg16_di" bpmnElement="SubProcess_006dg16" isExpanded="true">
        <dc:Bounds x="630" y="270" width="388" height="145" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_0dq3i8l_di" bpmnElement="EndEvent_0dq3i8l">
        <dc:Bounds x="942" y="317" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_0cj9pdg_di" bpmnElement="ServiceTask_0cj9pdg">
        <dc:Bounds x="780" y="295" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="StartEvent_08k6psq_di" bpmnElement="StartEvent_0kpitfv">
        <dc:Bounds x="682" y="317" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="669" y="360" width="65" height="27" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_1c82aad_di" bpmnElement="SequenceFlow_1c82aad">
        <di:waypoint x="718" y="335" />
        <di:waypoint x="780" y="335" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_0vkqogh_di" bpmnElement="SequenceFlow_0vkqogh">
        <di:waypoint x="880" y="335" />
        <di:waypoint x="942" y="335" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_03jyud1_di" bpmnElement="SequenceFlow_03jyud1">
        <di:waypoint x="696" y="185" />
        <di:waypoint x="740" y="185" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_1ey1yvq_di" bpmnElement="SequenceFlow_1ey1yvq">
        <di:waypoint x="840" y="185" />
        <di:waypoint x="882" y="185" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_0b1strv_di" bpmnElement="SequenceFlow_0b1strv">
        <di:waypoint x="248" y="270" />
        <di:waypoint x="344" y="270" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_1aytoqp_di" bpmnElement="SequenceFlow_1aytoqp">
        <di:waypoint x="444" y="270" />
        <di:waypoint x="530" y="270" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_0ogmd2w_di" bpmnElement="SequenceFlow_0ogmd2w">
        <di:waypoint x="1120" y="260" />
        <di:waypoint x="1202" y="260" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
`;

const mockMigrationOperation: BatchOperation = {
  batchOperationKey: '653ed5e6-49ed-4675-85bf-2c54a94d8180',
  batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
  startDate: '2023-09-29T16:23:10.684+0000',
  operationsTotalCount: 3,
  operationsFailedCount: 0,
  operationsCompletedCount: 3,
  state: 'COMPLETED',
};

const mockDeleteProcess = {
  id: 'b5aa7d44-3a4b-4dfb-9694-e3cf582a80a8',
  username: 'demo',
  processInstanceKey: 'b5aa7d44-3a4b-4dfb-9694-e3cf582a80a8',
  name: 'Order Process - Version 1',
  type: 'DELETE_PROCESS_DEFINITION',
  startDate: '2023-10-13T08:49:44.008+0200',
  endDate: null,
  instancesCount: 0,
  operationsTotalCount: 1,
  operationsFinishedCount: 0,
} as const;

const mockBatchOperationItemsWithError: QueryBatchOperationItemsResponseBody = {
  items: [
    {
      batchOperationKey: 'bf547ac3-9a35-45b9-ab06-b80b43785153',
      itemKey: 'error-item-1',
      processInstanceKey: '6755399441062827',
      state: 'FAILED',
      processedDate: '2023-08-14T05:47:10.000+0000',
      errorMessage: 'Batch Operation Error Message',
      operationType: 'RESOLVE_INCIDENT',
    },
    {
      batchOperationKey: 'bf547ac3-9a35-45b9-ab06-b80b43785153',
      itemKey: 'error-item-2',
      processInstanceKey: '6755399441062826',
      state: 'COMPLETED',
      processedDate: '2023-08-14T05:47:08.000+0000',
      operationType: 'RESOLVE_INCIDENT',
    },
  ],
  page: {totalItems: 2},
};

export {
  mockProcessDefinitions,
  mockBatchOperations,
  mockProcessInstances,
  mockProcessInstancesWithOperationError,
  mockStatistics,
  mockProcessXml,
  mockResponses,
  mockNewDeleteOperation,
  mockDeleteProcess,
  mockOrderProcessDefinitions,
  mockOrderProcessInstances,
  mockFinishedOrderProcessInstances,
  mockOrderProcessInstancesWithFailedOperations,
  mockOrderProcessV2Instances,
  mockMigrationOperation,
  mockAhspProcessDefinitions,
  mockAhspProcessInstances,
  mockBatchOperationItemsWithError,
};
